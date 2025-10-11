"""
Video Copy-Move Forgery Detection
==================================

ALGORITHM PRINCIPLE:
-------------------
Copy-move forgery is a common manipulation technique in deepfake videos where
a region from one frame is copied and pasted to another location in the same
frame or across frames. This creates temporal and spatial inconsistencies.

Detection approach:
1. Extract keyframes from video
2. For each frame, detect duplicate regions using SIFT/ORB feature matching
3. Analyze temporal consistency across frames
4. Generate heatmap showing suspicious regions

USAGE:
------
Command line:
    python video_copy_move.py input_video.mp4 output_dir/

Python API:
    from video_copy_move import detect_copy_move
    results = detect_copy_move('video.mp4', 'output/')

OUTPUT:
-------
output_dir/
├── copy_move_heatmap.png       # Visualization of detected regions
├── copy_move_frames/           # Individual frame analysis
│   ├── frame_0001.png
│   ├── frame_0010.png
│   └── ...
└── copy_move_results.json      # Detection results in JSON format

JSON FORMAT:
{
    "video_path": "input.mp4",
    "total_frames": 150,
    "analyzed_frames": 30,
    "detection_score": 0.73,
    "is_manipulated": true,
    "suspicious_frames": [5, 12, 23],
    "details": [...]
}

PARAMETERS:
-----------
- num_keyframes: Number of frames to analyze (default: 30)
- similarity_threshold: Threshold for duplicate detection (default: 0.8)
- min_matches: Minimum feature matches to consider as copy-move (default: 10)
"""

import cv2
import numpy as np
import json
import sys
from pathlib import Path
from typing import Tuple, List, Dict
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle


def extract_keyframes(video_path: str, num_frames: int = 30) -> List[np.ndarray]:
    """
    Extract evenly distributed keyframes from video
    
    Args:
        video_path: Path to input video
        num_frames: Number of frames to extract
        
    Returns:
        List of frames as numpy arrays
    """
    cap = cv2.VideoCapture(video_path)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    if total_frames == 0:
        raise ValueError(f"Cannot read video: {video_path}")
    
    # Calculate frame indices to extract
    indices = np.linspace(0, total_frames - 1, num_frames, dtype=int)
    
    frames = []
    for idx in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ret, frame = cap.read()
        if ret:
            frames.append(frame)
    
    cap.release()
    return frames


def detect_copy_move_in_frame(frame: np.ndarray, 
                               min_matches: int = 10,
                               similarity_threshold: float = 0.8) -> Tuple[bool, List, np.ndarray]:
    """
    Detect copy-move forgery in a single frame using SIFT feature matching
    
    Algorithm:
    1. Convert frame to grayscale
    2. Detect SIFT keypoints and descriptors
    3. Match features with themselves using FLANN
    4. Filter matches based on distance ratio test
    5. Find spatial clusters indicating copied regions
    
    Args:
        frame: Input frame (BGR format)
        min_matches: Minimum number of matches to consider forgery
        similarity_threshold: Distance ratio threshold for good matches
        
    Returns:
        (is_forged, match_locations, visualization_image)
    """
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    
    # Initialize SIFT detector
    sift = cv2.SIFT_create()
    keypoints, descriptors = sift.detectAndCompute(gray, None)
    
    if descriptors is None or len(keypoints) < min_matches:
        return False, [], frame.copy()
    
    # FLANN matcher for efficient matching
    FLANN_INDEX_KDTREE = 1
    index_params = dict(algorithm=FLANN_INDEX_KDTREE, trees=5)
    search_params = dict(checks=50)
    flann = cv2.FlannBasedMatcher(index_params, search_params)
    
    # Match features with themselves
    matches = flann.knnMatch(descriptors, descriptors, k=2)
    
    # Apply Lowe's ratio test to find good matches
    good_matches = []
    for match_pair in matches:
        if len(match_pair) == 2:
            m, n = match_pair
            # Exclude self-matches and apply ratio test
            if m.trainIdx != m.queryIdx and m.distance < similarity_threshold * n.distance:
                good_matches.append(m)
    
    # Detect copy-move if enough good matches found
    is_forged = len(good_matches) >= min_matches
    
    # Get match locations for visualization
    match_locations = []
    if is_forged:
        for match in good_matches:
            pt1 = keypoints[match.queryIdx].pt
            pt2 = keypoints[match.trainIdx].pt
            # Only consider if points are spatially separated
            distance = np.sqrt((pt1[0] - pt2[0])**2 + (pt1[1] - pt2[1])**2)
            if distance > 50:  # Minimum distance threshold
                match_locations.append((pt1, pt2))
    
    # Create visualization
    vis_frame = frame.copy()
    for pt1, pt2 in match_locations:
        cv2.circle(vis_frame, (int(pt1[0]), int(pt1[1])), 5, (0, 255, 0), 2)
        cv2.circle(vis_frame, (int(pt2[0]), int(pt2[1])), 5, (0, 0, 255), 2)
        cv2.line(vis_frame, (int(pt1[0]), int(pt1[1])), 
                 (int(pt2[0]), int(pt2[1])), (255, 0, 0), 1)
    
    return is_forged, match_locations, vis_frame


def generate_heatmap(frames: List[np.ndarray], 
                     detection_results: List[Dict]) -> np.ndarray:
    """
    Generate heatmap showing suspicious regions across all frames
    
    Args:
        frames: List of analyzed frames
        detection_results: Detection results for each frame
        
    Returns:
        Heatmap image as numpy array
    """
    if not frames:
        return np.zeros((480, 640, 3), dtype=np.uint8)
    
    h, w = frames[0].shape[:2]
    heatmap = np.zeros((h, w), dtype=np.float32)
    
    # Accumulate match locations
    for result in detection_results:
        for pt1, pt2 in result['match_locations']:
            cv2.circle(heatmap, (int(pt1[0]), int(pt1[1])), 20, 1.0, -1)
            cv2.circle(heatmap, (int(pt2[0]), int(pt2[1])), 20, 1.0, -1)
    
    # Normalize and apply colormap
    if heatmap.max() > 0:
        heatmap = heatmap / heatmap.max()
    
    heatmap_colored = cv2.applyColorMap((heatmap * 255).astype(np.uint8), 
                                        cv2.COLORMAP_JET)
    
    return heatmap_colored


def detect_copy_move(video_path: str, 
                     output_dir: str,
                     num_keyframes: int = 30,
                     min_matches: int = 10,
                     similarity_threshold: float = 0.8) -> Dict:
    """
    Main function to detect copy-move forgery in video
    
    Args:
        video_path: Path to input video file
        output_dir: Directory to save output files
        num_keyframes: Number of frames to analyze
        min_matches: Minimum matches to consider forgery
        similarity_threshold: Threshold for feature matching
        
    Returns:
        Dictionary containing detection results
    """
    print(f"[INFO] Analyzing video: {video_path}")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    frames_dir = output_path / "copy_move_frames"
    frames_dir.mkdir(exist_ok=True)
    
    # Extract keyframes
    print(f"[INFO] Extracting {num_keyframes} keyframes...")
    frames = extract_keyframes(video_path, num_keyframes)
    
    # Analyze each frame
    print("[INFO] Detecting copy-move forgery...")
    detection_results = []
    suspicious_frame_indices = []
    
    for idx, frame in enumerate(frames):
        is_forged, match_locations, vis_frame = detect_copy_move_in_frame(
            frame, min_matches, similarity_threshold
        )
        
        result = {
            'frame_index': idx,
            'is_forged': is_forged,
            'num_matches': len(match_locations),
            'match_locations': match_locations
        }
        detection_results.append(result)
        
        if is_forged:
            suspicious_frame_indices.append(idx)
            # Save visualization for suspicious frames
            output_frame_path = frames_dir / f"frame_{idx:04d}.png"
            cv2.imwrite(str(output_frame_path), vis_frame)
    
    # Calculate detection score
    num_forged = sum(1 for r in detection_results if r['is_forged'])
    detection_score = num_forged / len(frames) if frames else 0.0
    
    # Determine if video is manipulated (threshold: 20% of frames)
    is_manipulated = detection_score > 0.2
    
    # Generate heatmap
    print("[INFO] Generating heatmap...")
    heatmap = generate_heatmap(frames, detection_results)
    heatmap_path = output_path / "copy_move_heatmap.png"
    cv2.imwrite(str(heatmap_path), heatmap)
    
    # Prepare results
    results = {
        'video_path': video_path,
        'total_frames': len(frames),
        'analyzed_frames': num_keyframes,
        'detection_score': float(detection_score),
        'is_manipulated': bool(is_manipulated),
        'suspicious_frames': suspicious_frame_indices,
        'num_suspicious_frames': len(suspicious_frame_indices),
        'details': [
            {
                'frame_index': r['frame_index'],
                'is_forged': r['is_forged'],
                'num_matches': r['num_matches']
            }
            for r in detection_results
        ]
    }
    
    # Save JSON results
    json_path = output_path / "copy_move_results.json"
    with open(json_path, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"[INFO] Detection score: {detection_score:.2f}")
    print(f"[INFO] Is manipulated: {is_manipulated}")
    print(f"[INFO] Suspicious frames: {len(suspicious_frame_indices)}")
    print(f"[INFO] Results saved to: {output_dir}")
    
    return results


def main():
    """
    Command line interface
    
    Usage:
        python video_copy_move.py input_video.mp4 output_dir/
    """
    if len(sys.argv) < 3:
        print("Usage: python video_copy_move.py <input_video> <output_dir>")
        print("\nExample:")
        print("  python video_copy_move.py video.mp4 results/")
        sys.exit(1)
    
    video_path = sys.argv[1]
    output_dir = sys.argv[2]
    
    # Optional parameters
    num_keyframes = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    min_matches = int(sys.argv[4]) if len(sys.argv) > 4 else 10
    similarity_threshold = float(sys.argv[5]) if len(sys.argv) > 5 else 0.8
    
    # Run detection
    results = detect_copy_move(
        video_path, 
        output_dir,
        num_keyframes=num_keyframes,
        min_matches=min_matches,
        similarity_threshold=similarity_threshold
    )
    
    print("\n" + "="*60)
    print("COPY-MOVE DETECTION COMPLETE")
    print("="*60)
    print(f"Detection Score: {results['detection_score']:.2%}")
    print(f"Verdict: {'MANIPULATED' if results['is_manipulated'] else 'AUTHENTIC'}")
    print(f"Suspicious Frames: {results['num_suspicious_frames']}/{results['analyzed_frames']}")
    print("="*60)


if __name__ == "__main__":
    main()
