"""
Optical Flow Analysis for Deepfake Detection
============================================

ALGORITHM PRINCIPLE:
-------------------
Optical flow represents the pattern of apparent motion of objects in a video
caused by the relative motion between the camera and the scene. Real videos
captured by physical cameras exhibit smooth, consistent optical flow patterns
that follow physical laws. AI-generated videos often show:

1. Inconsistent motion vectors (sudden jumps, unnatural movements)
2. Flow field discontinuities (broken motion patterns)
3. Irregular flow magnitude distributions
4. Abnormal flow orientations in static regions

Detection approach:
1. Compute dense optical flow between consecutive frames using Farneback algorithm
2. Analyze flow magnitude statistics (velocity distribution)
3. Detect flow discontinuities and inconsistencies
4. Calculate flow smoothness and coherence metrics
5. Compare against natural motion patterns

Key insight: Deepfakes struggle to maintain physically plausible motion patterns
across frames, especially in complex scenes with multiple moving objects.

USAGE:
------
Command line:
    python optical_flow_analysis.py input_video.mp4 output_dir/

Python API:
    from optical_flow_analysis import analyze_optical_flow
    results = analyze_optical_flow('video.mp4', 'output/')

OUTPUT:
-------
output_dir/
├── flow_visualization.png         # Optical flow vector field visualization
├── flow_magnitude_plot.png        # Flow magnitude distribution over time
├── flow_anomaly_heatmap.png       # Heatmap of flow anomalies
├── flow_frames/                   # Individual flow visualizations
│   ├── flow_0001.png
│   ├── flow_0010.png
│   └── ...
└── optical_flow_results.json      # Detection results

JSON FORMAT:
{
    "video_path": "input.mp4",
    "total_frames": 200,
    "avg_flow_magnitude": 3.45,
    "flow_std": 1.23,
    "flow_smoothness": 0.78,
    "anomaly_score": 0.62,
    "is_manipulated": true,
    "anomalous_frames": [12, 34, 56],
    "flow_statistics": {...}
}

PARAMETERS:
-----------
- sample_frames: Number of frames to analyze (default: 50)
- flow_threshold: Threshold for significant motion (default: 1.0 pixels)
- smoothness_window: Window size for smoothness calculation (default: 5 frames)
- anomaly_sensitivity: Sensitivity for anomaly detection (default: 2.5 std)
"""

import cv2
import numpy as np
import json
import sys
from pathlib import Path
from typing import List, Dict, Tuple
import matplotlib.pyplot as plt
from matplotlib import patches


def compute_optical_flow(frame1: np.ndarray, frame2: np.ndarray) -> Tuple[np.ndarray, Dict]:
    """
    Compute dense optical flow between two consecutive frames using Farneback algorithm
    
    Farneback algorithm:
    - Polynomial expansion-based approach
    - Computes dense flow field (motion vector for every pixel)
    - More robust than sparse methods like Lucas-Kanade
    
    Args:
        frame1: First frame (BGR)
        frame2: Second frame (BGR)
        
    Returns:
        (flow_field, flow_statistics)
        - flow_field: 2-channel array (dx, dy) for each pixel
        - flow_statistics: Dictionary with flow metrics
    """
    # Convert to grayscale
    gray1 = cv2.cvtColor(frame1, cv2.COLOR_BGR2GRAY)
    gray2 = cv2.cvtColor(frame2, cv2.COLOR_BGR2GRAY)
    
    # Compute optical flow using Farneback algorithm
    flow = cv2.calcOpticalFlowFarneback(
        gray1, gray2,
        None,
        pyr_scale=0.5,      # Image pyramid scale
        levels=3,           # Number of pyramid layers
        winsize=15,         # Averaging window size
        iterations=3,       # Number of iterations at each pyramid level
        poly_n=5,           # Size of pixel neighborhood for polynomial expansion
        poly_sigma=1.2,     # Standard deviation of Gaussian for polynomial expansion
        flags=0
    )
    
    # Calculate flow magnitude and angle
    magnitude, angle = cv2.cartToPolar(flow[..., 0], flow[..., 1])
    
    # Calculate statistics
    stats = {
        'mean_magnitude': float(np.mean(magnitude)),
        'std_magnitude': float(np.std(magnitude)),
        'max_magnitude': float(np.max(magnitude)),
        'median_magnitude': float(np.median(magnitude)),
        'mean_angle': float(np.mean(angle)),
        'flow_density': float(np.sum(magnitude > 0.5) / magnitude.size)  # Percentage of moving pixels
    }
    
    return flow, stats


def calculate_flow_smoothness(flow: np.ndarray) -> float:
    """
    Calculate smoothness of optical flow field
    
    Smoothness is measured by the consistency of flow vectors in local neighborhoods.
    Real videos have smooth flow fields, while deepfakes often show abrupt changes.
    
    Method:
    - Compute gradient of flow field (second derivative)
    - Lower gradient = smoother flow = more realistic
    
    Args:
        flow: Optical flow field (H, W, 2)
        
    Returns:
        Smoothness score (0-1, higher = smoother = more realistic)
    """
    # Calculate spatial gradients of flow
    dx = cv2.Sobel(flow[..., 0], cv2.CV_64F, 1, 0, ksize=3)
    dy = cv2.Sobel(flow[..., 1], cv2.CV_64F, 0, 1, ksize=3)
    
    # Calculate gradient magnitude (measures flow consistency)
    gradient_mag = np.sqrt(dx**2 + dy**2)
    
    # Normalize to [0, 1] (lower gradient = smoother)
    max_grad = np.percentile(gradient_mag, 95)  # Use 95th percentile to avoid outliers
    if max_grad > 0:
        smoothness = 1.0 - np.clip(np.mean(gradient_mag) / max_grad, 0, 1)
    else:
        smoothness = 1.0
    
    return float(smoothness)


def detect_flow_anomalies(flow_stats: List[Dict], sensitivity: float = 2.5) -> List[int]:
    """
    Detect anomalous frames based on optical flow statistics
    
    Anomalies indicate:
    - Sudden motion changes (fake transitions)
    - Inconsistent flow patterns
    - Unnatural motion distributions
    
    Args:
        flow_stats: List of flow statistics for each frame
        sensitivity: Number of standard deviations for anomaly threshold
        
    Returns:
        List of anomalous frame indices
    """
    if len(flow_stats) < 10:
        return []
    
    # Extract magnitude values
    magnitudes = np.array([s['mean_magnitude'] for s in flow_stats])
    smoothness_values = np.array([s.get('smoothness', 0) for s in flow_stats])
    
    # Detect magnitude anomalies (sudden motion spikes)
    mag_mean = np.mean(magnitudes)
    mag_std = np.std(magnitudes)
    mag_threshold_high = mag_mean + sensitivity * mag_std
    mag_threshold_low = mag_mean - sensitivity * mag_std
    
    mag_anomalies = np.where((magnitudes > mag_threshold_high) | 
                             (magnitudes < mag_threshold_low))[0].tolist()
    
    # Detect smoothness anomalies (rough motion)
    if len(smoothness_values) > 0 and smoothness_values.max() > 0:
        smooth_mean = np.mean(smoothness_values)
        smooth_std = np.std(smoothness_values)
        smooth_threshold = smooth_mean - sensitivity * smooth_std
        smooth_anomalies = np.where(smoothness_values < smooth_threshold)[0].tolist()
    else:
        smooth_anomalies = []
    
    # Combine anomalies
    all_anomalies = list(set(mag_anomalies + smooth_anomalies))
    all_anomalies.sort()
    
    return all_anomalies


def visualize_optical_flow(frame: np.ndarray, flow: np.ndarray) -> np.ndarray:
    """
    Create visualization of optical flow using HSV color coding
    
    Visualization scheme:
    - Hue: Flow direction (angle)
    - Saturation: Flow magnitude (speed)
    - Value: Fixed at maximum brightness
    
    Args:
        frame: Original frame (BGR)
        flow: Optical flow field
        
    Returns:
        Flow visualization image (BGR)
    """
    h, w = flow.shape[:2]
    
    # Calculate magnitude and angle
    magnitude, angle = cv2.cartToPolar(flow[..., 0], flow[..., 1])
    
    # Create HSV image
    hsv = np.zeros((h, w, 3), dtype=np.uint8)
    hsv[..., 0] = angle * 180 / np.pi / 2  # Hue: direction
    hsv[..., 1] = cv2.normalize(magnitude, None, 0, 255, cv2.NORM_MINMAX)  # Saturation: magnitude
    hsv[..., 2] = 255  # Value: maximum brightness
    
    # Convert to BGR for display
    flow_vis = cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR)
    
    # Blend with original frame for context
    blended = cv2.addWeighted(frame, 0.5, flow_vis, 0.5, 0)
    
    return blended


def generate_flow_plot(flow_stats: List[Dict], 
                       anomaly_indices: List[int],
                       output_path: str):
    """
    Generate plot showing optical flow metrics over time
    
    Args:
        flow_stats: List of flow statistics
        anomaly_indices: Frames with detected anomalies
        output_path: Path to save plot
    """
    frame_indices = list(range(len(flow_stats)))
    magnitudes = [s['mean_magnitude'] for s in flow_stats]
    smoothness = [s.get('smoothness', 0) for s in flow_stats]
    flow_density = [s['flow_density'] for s in flow_stats]
    
    fig, axes = plt.subplots(3, 1, figsize=(12, 9))
    
    # Flow magnitude plot
    axes[0].plot(frame_indices, magnitudes, 'b-', linewidth=1.5, label='Flow Magnitude')
    axes[0].scatter([i for i in anomaly_indices],
                   [magnitudes[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5, label='Anomaly')
    axes[0].axhline(y=np.mean(magnitudes), color='g', linestyle='--',
                    label=f'Mean: {np.mean(magnitudes):.2f}')
    axes[0].set_ylabel('Magnitude (pixels)')
    axes[0].set_title('Optical Flow Magnitude Over Time')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)
    
    # Flow smoothness plot
    axes[1].plot(frame_indices, smoothness, 'g-', linewidth=1.5, label='Smoothness')
    axes[1].scatter([i for i in anomaly_indices],
                   [smoothness[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5)
    axes[1].axhline(y=np.mean(smoothness), color='b', linestyle='--',
                    label=f'Mean: {np.mean(smoothness):.3f}')
    axes[1].set_ylabel('Smoothness Score')
    axes[1].set_title('Flow Field Smoothness Over Time')
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)
    
    # Flow density plot
    axes[2].plot(frame_indices, flow_density, 'm-', linewidth=1.5, label='Flow Density')
    axes[2].scatter([i for i in anomaly_indices],
                   [flow_density[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5)
    axes[2].axhline(y=np.mean(flow_density), color='c', linestyle='--',
                    label=f'Mean: {np.mean(flow_density):.2f}')
    axes[2].set_xlabel('Frame Index')
    axes[2].set_ylabel('Flow Density')
    axes[2].set_title('Motion Density Over Time (% of moving pixels)')
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def analyze_optical_flow(video_path: str,
                         output_dir: str,
                         sample_frames: int = 50,
                         anomaly_sensitivity: float = 2.5,
                         progress_callback=None) -> Dict:
    """
    Main function to analyze optical flow in video
    
    Args:
        video_path: Path to input video file
        output_dir: Directory to save output files
        sample_frames: Number of frames to analyze
        anomaly_sensitivity: Sensitivity for anomaly detection
        
    Returns:
        Dictionary containing analysis results
    """
    print(f"[INFO] Analyzing video: {video_path}")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    flow_frames_dir = output_path / "flow_frames"
    flow_frames_dir.mkdir(exist_ok=True)
    
    # Open video
    cap = cv2.VideoCapture(video_path)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    
    # Calculate frame indices to analyze
    if total_frames < sample_frames:
        frame_indices = list(range(total_frames))
    else:
        frame_indices = np.linspace(0, total_frames - 2, sample_frames, dtype=int).tolist()
    
    print(f"[INFO] Video: {total_frames} frames @ {fps:.2f} FPS")
    print(f"[INFO] Analyzing {len(frame_indices)} frame pairs...")
    
    flow_stats = []
    prev_frame = None
    processed = 0
    
    for idx in frame_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ret1, frame1 = cap.read()
        ret2, frame2 = cap.read()
        
        if not (ret1 and ret2):
            continue
        
        # Compute optical flow
        flow, stats = compute_optical_flow(frame1, frame2)
        
        # Calculate flow smoothness
        smoothness = calculate_flow_smoothness(flow)
        stats['smoothness'] = smoothness
        stats['frame_index'] = idx
        
        flow_stats.append(stats)
        
        # Save visualization for every 10th frame
        if len(flow_stats) % 10 == 0:
            flow_vis = visualize_optical_flow(frame1, flow)
            vis_path = flow_frames_dir / f"flow_{idx:04d}.png"
            cv2.imwrite(str(vis_path), flow_vis)
        
        processed += 1
        if processed % 5 == 0:
            print(f"[INFO] Processed {len(flow_stats)}/{len(frame_indices)} frame pairs...")
            if progress_callback:
                local_pct = 25 + int(50 * (processed / max(1, len(frame_indices))))
                try:
                    progress_callback(local_pct, f"Optical flow processed {processed}/{len(frame_indices)} pairs")
                except Exception:
                    pass
    
    cap.release()
    
    print(f"[INFO] Detecting flow anomalies...")
    
    # Detect anomalies
    anomaly_indices = detect_flow_anomalies(flow_stats, anomaly_sensitivity)
    
    # Calculate overall statistics
    magnitudes = [s['mean_magnitude'] for s in flow_stats]
    smoothness_values = [s['smoothness'] for s in flow_stats]
    
    avg_magnitude = float(np.mean(magnitudes))
    std_magnitude = float(np.std(magnitudes))
    avg_smoothness = float(np.mean(smoothness_values))
    std_smoothness = float(np.std(smoothness_values))
    
    # Calculate anomaly score
    anomaly_ratio = len(anomaly_indices) / len(flow_stats) if flow_stats else 0
    smoothness_score = avg_smoothness  # Higher = better
    
    # Combined anomaly score (0-1, higher = more suspicious)
    anomaly_score = anomaly_ratio * 0.5 + (1 - smoothness_score) * 0.5
    
    # Determine if manipulated (threshold: 0.35)
    is_manipulated = anomaly_score > 0.35
    
    print(f"[INFO] Generating visualizations...")
    
    # Generate flow plot
    plot_path = output_path / "flow_magnitude_plot.png"
    generate_flow_plot(flow_stats, anomaly_indices, str(plot_path))
    
    # Create summary visualization (first frame with flow)
    cap = cv2.VideoCapture(video_path)
    ret1, frame1 = cap.read()
    ret2, frame2 = cap.read()
    if ret1 and ret2:
        flow, _ = compute_optical_flow(frame1, frame2)
        flow_vis = visualize_optical_flow(frame1, flow)
        summary_path = output_path / "flow_visualization.png"
        cv2.imwrite(str(summary_path), flow_vis)
    cap.release()
    
    # Prepare results
    results = {
        'video_path': video_path,
        'total_frames': total_frames,
        'analyzed_frames': len(flow_stats),
        'fps': float(fps),
        'avg_flow_magnitude': avg_magnitude,
        'std_flow_magnitude': std_magnitude,
        'avg_flow_smoothness': avg_smoothness,
        'std_flow_smoothness': std_smoothness,
        'anomaly_score': float(anomaly_score),
        'is_manipulated': bool(is_manipulated),
        'num_anomalies': len(anomaly_indices),
        'anomalous_frames': anomaly_indices,
        'flow_statistics': flow_stats
    }
    
    # Save JSON results
    json_path = output_path / "optical_flow_results.json"
    with open(json_path, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"[INFO] Anomaly score: {anomaly_score:.3f}")
    print(f"[INFO] Is manipulated: {is_manipulated}")
    print(f"[INFO] Flow anomalies: {len(anomaly_indices)}")
    print(f"[INFO] Avg smoothness: {avg_smoothness:.3f}")
    print(f"[INFO] Results saved to: {output_dir}")
    
    return results


def main():
    """
    Command line interface
    
    Usage:
        python optical_flow_analysis.py <input_video> <output_dir> [sample_frames] [sensitivity]
    """
    if len(sys.argv) < 3:
        print("Usage: python optical_flow_analysis.py <input_video> <output_dir> [sample_frames] [sensitivity]")
        print("\nExample:")
        print("  python optical_flow_analysis.py video.mp4 results/")
        print("  python optical_flow_analysis.py video.mp4 results/ 50 2.5")
        print("\nParameters:")
        print("  sample_frames: Number of frame pairs to analyze (default: 50)")
        print("  sensitivity: Anomaly detection sensitivity (default: 2.5)")
        sys.exit(1)
    
    video_path = sys.argv[1]
    output_dir = sys.argv[2]
    sample_frames = int(sys.argv[3]) if len(sys.argv) > 3 else 50
    sensitivity = float(sys.argv[4]) if len(sys.argv) > 4 else 2.5
    
    # Run analysis
    results = analyze_optical_flow(
        video_path,
        output_dir,
        sample_frames=sample_frames,
        anomaly_sensitivity=sensitivity
    )
    
    print("\n" + "="*60)
    print("OPTICAL FLOW ANALYSIS COMPLETE")
    print("="*60)
    print(f"Anomaly Score: {results['anomaly_score']:.3f}")
    print(f"Verdict: {'MANIPULATED' if results['is_manipulated'] else 'AUTHENTIC'}")
    print(f"Detected Anomalies: {results['num_anomalies']}/{results['analyzed_frames']}")
    print(f"Avg Flow Magnitude: {results['avg_flow_magnitude']:.2f} ± {results['std_flow_magnitude']:.2f} px")
    print(f"Avg Flow Smoothness: {results['avg_flow_smoothness']:.3f} ± {results['std_flow_smoothness']:.3f}")
    print("="*60)


if __name__ == "__main__":
    main()
