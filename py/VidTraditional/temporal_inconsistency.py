"""
Temporal Inconsistency Detection for Deepfake Videos
====================================================

ALGORITHM PRINCIPLE:
-------------------
AI-generated videos often have temporal inconsistencies because the generation
process treats each frame somewhat independently. Real videos maintain smooth
transitions between consecutive frames due to physical camera motion and scene
dynamics.

Detection approach:
1. Extract consecutive frames from video
2. Calculate frame-to-frame differences using multiple metrics:
   - Structural Similarity Index (SSIM)
   - Peak Signal-to-Noise Ratio (PSNR)
   - Histogram correlation
   - Edge difference
3. Detect sudden jumps or anomalies in temporal consistency
4. Analyze motion flow smoothness

Key insight: Deepfakes show irregular temporal patterns, with unexpected
spikes in frame differences, especially in facial regions.

USAGE:
------
Command line:
    python temporal_inconsistency.py input_video.mp4 output_dir/

Python API:
    from temporal_inconsistency import detect_temporal_inconsistency
    results = detect_temporal_inconsistency('video.mp4', 'output/')

OUTPUT:
-------
output_dir/
├── temporal_plot.png              # Graph showing temporal metrics
├── temporal_heatmap.png           # Heatmap of frame differences
├── anomaly_frames/                # Frames with detected anomalies
│   ├── anomaly_frame_0023.png
│   └── ...
└── temporal_results.json          # Detection results

JSON FORMAT:
{
    "video_path": "input.mp4",
    "total_frames": 200,
    "fps": 30.0,
    "avg_ssim": 0.92,
    "ssim_std": 0.08,
    "anomaly_score": 0.65,
    "is_manipulated": true,
    "anomaly_frames": [23, 45, 67],
    "temporal_metrics": [...]
}

PARAMETERS:
-----------
- ssim_threshold: Threshold for SSIM drops (default: 0.85)
- psnr_threshold: Threshold for PSNR drops (default: 25 dB)
- anomaly_sensitivity: Sensitivity for anomaly detection (default: 2.0 std)
- sample_rate: Frame sampling rate for analysis (default: 1, analyze all frames)
"""

import cv2
import numpy as np
import json
import sys
from pathlib import Path
from typing import List, Dict, Tuple
import matplotlib.pyplot as plt
from skimage.metrics import structural_similarity as ssim
from skimage.metrics import peak_signal_noise_ratio as psnr


def calculate_frame_metrics(frame1: np.ndarray, frame2: np.ndarray) -> Dict:
    """
    Calculate multiple temporal consistency metrics between two consecutive frames
    
    Metrics computed:
    - SSIM: Structural similarity (higher = more similar)
    - PSNR: Peak signal-to-noise ratio (higher = more similar)
    - Histogram correlation: Color distribution similarity
    - Edge difference: Change in edge content
    
    Args:
        frame1: First frame (BGR)
        frame2: Second frame (BGR)
        
    Returns:
        Dictionary containing all metrics
    """
    # Convert to grayscale for SSIM and PSNR
    gray1 = cv2.cvtColor(frame1, cv2.COLOR_BGR2GRAY)
    gray2 = cv2.cvtColor(frame2, cv2.COLOR_BGR2GRAY)
    
    # SSIM: Structural Similarity Index
    ssim_value = ssim(gray1, gray2)
    
    # PSNR: Peak Signal-to-Noise Ratio
    psnr_value = psnr(gray1, gray2)
    
    # Histogram correlation (color consistency)
    hist1 = cv2.calcHist([frame1], [0, 1, 2], None, [8, 8, 8], [0, 256, 0, 256, 0, 256])
    hist2 = cv2.calcHist([frame2], [0, 1, 2], None, [8, 8, 8], [0, 256, 0, 256, 0, 256])
    hist1 = cv2.normalize(hist1, hist1).flatten()
    hist2 = cv2.normalize(hist2, hist2).flatten()
    hist_corr = cv2.compareHist(hist1, hist2, cv2.HISTCMP_CORREL)
    
    # Edge difference (detect abrupt content changes)
    edges1 = cv2.Canny(gray1, 50, 150)
    edges2 = cv2.Canny(gray2, 50, 150)
    edge_diff = np.sum(np.abs(edges1.astype(float) - edges2.astype(float))) / edges1.size
    
    # Absolute pixel difference (MSE)
    mse = np.mean((gray1.astype(float) - gray2.astype(float)) ** 2)
    
    return {
        'ssim': float(ssim_value),
        'psnr': float(psnr_value),
        'hist_corr': float(hist_corr),
        'edge_diff': float(edge_diff),
        'mse': float(mse)
    }


def detect_anomalies(metrics: List[Dict], sensitivity: float = 2.0) -> List[int]:
    """
    Detect temporal anomalies using statistical analysis
    
    Anomalies are frames where metrics deviate significantly from the mean,
    indicating possible manipulation or generation artifacts.
    
    Args:
        metrics: List of metric dictionaries for each frame transition
        sensitivity: Number of standard deviations for anomaly threshold
        
    Returns:
        List of frame indices with detected anomalies
    """
    if len(metrics) < 10:
        return []
    
    # Extract SSIM values (most reliable indicator)
    ssim_values = np.array([m['ssim'] for m in metrics])
    
    # Calculate statistics
    mean_ssim = np.mean(ssim_values)
    std_ssim = np.std(ssim_values)
    
    # Detect outliers (sudden drops in similarity)
    threshold = mean_ssim - sensitivity * std_ssim
    anomaly_indices = np.where(ssim_values < threshold)[0].tolist()
    
    # Also check for PSNR drops
    psnr_values = np.array([m['psnr'] for m in metrics])
    mean_psnr = np.mean(psnr_values)
    std_psnr = np.std(psnr_values)
    psnr_threshold = mean_psnr - sensitivity * std_psnr
    psnr_anomalies = np.where(psnr_values < psnr_threshold)[0].tolist()
    
    # Combine both anomaly detections
    anomaly_indices = list(set(anomaly_indices + psnr_anomalies))
    anomaly_indices.sort()
    
    return anomaly_indices


def generate_temporal_plot(metrics: List[Dict], 
                          anomaly_indices: List[int],
                          output_path: str):
    """
    Generate visualization of temporal metrics over time
    
    Creates a multi-panel plot showing:
    - SSIM over time
    - PSNR over time
    - Histogram correlation
    - Anomaly markers
    
    Args:
        metrics: List of frame metrics
        anomaly_indices: Frames with detected anomalies
        output_path: Path to save plot
    """
    frame_indices = list(range(len(metrics)))
    ssim_values = [m['ssim'] for m in metrics]
    psnr_values = [m['psnr'] for m in metrics]
    hist_corr_values = [m['hist_corr'] for m in metrics]
    edge_diff_values = [m['edge_diff'] for m in metrics]
    
    fig, axes = plt.subplots(4, 1, figsize=(12, 10))
    
    # SSIM plot
    axes[0].plot(frame_indices, ssim_values, 'b-', linewidth=1.5, label='SSIM')
    axes[0].scatter([i for i in anomaly_indices], 
                   [ssim_values[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5, label='Anomaly')
    axes[0].axhline(y=np.mean(ssim_values), color='g', linestyle='--', 
                    label=f'Mean: {np.mean(ssim_values):.3f}')
    axes[0].set_ylabel('SSIM')
    axes[0].set_title('Structural Similarity Index Over Time')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)
    
    # PSNR plot
    axes[1].plot(frame_indices, psnr_values, 'g-', linewidth=1.5, label='PSNR')
    axes[1].scatter([i for i in anomaly_indices],
                   [psnr_values[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5)
    axes[1].axhline(y=np.mean(psnr_values), color='b', linestyle='--',
                    label=f'Mean: {np.mean(psnr_values):.1f} dB')
    axes[1].set_ylabel('PSNR (dB)')
    axes[1].set_title('Peak Signal-to-Noise Ratio Over Time')
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)
    
    # Histogram correlation plot
    axes[2].plot(frame_indices, hist_corr_values, 'm-', linewidth=1.5, label='Hist Corr')
    axes[2].scatter([i for i in anomaly_indices],
                   [hist_corr_values[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5)
    axes[2].axhline(y=np.mean(hist_corr_values), color='c', linestyle='--',
                    label=f'Mean: {np.mean(hist_corr_values):.3f}')
    axes[2].set_ylabel('Correlation')
    axes[2].set_title('Histogram Correlation Over Time')
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)
    
    # Edge difference plot
    axes[3].plot(frame_indices, edge_diff_values, 'r-', linewidth=1.5, label='Edge Diff')
    axes[3].scatter([i for i in anomaly_indices],
                   [edge_diff_values[i] for i in anomaly_indices],
                   c='red', s=50, zorder=5)
    axes[3].axhline(y=np.mean(edge_diff_values), color='orange', linestyle='--',
                    label=f'Mean: {np.mean(edge_diff_values):.4f}')
    axes[3].set_xlabel('Frame Index')
    axes[3].set_ylabel('Edge Difference')
    axes[3].set_title('Edge Content Difference Over Time')
    axes[3].legend()
    axes[3].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def generate_difference_heatmap(video_path: str,
                                anomaly_indices: List[int],
                                output_path: str,
                                max_frames: int = 10):
    """
    Generate heatmap showing frame differences at anomaly points
    
    Args:
        video_path: Path to video file
        anomaly_indices: Indices of anomalous frames
        output_path: Path to save heatmap
        max_frames: Maximum number of anomalies to visualize
    """
    if not anomaly_indices:
        return
    
    cap = cv2.VideoCapture(video_path)
    
    # Limit number of visualizations
    selected_indices = anomaly_indices[:max_frames]
    
    fig, axes = plt.subplots(len(selected_indices), 3, figsize=(12, 4 * len(selected_indices)))
    if len(selected_indices) == 1:
        axes = axes.reshape(1, -1)
    
    for i, frame_idx in enumerate(selected_indices):
        # Read frame and next frame
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
        ret1, frame1 = cap.read()
        ret2, frame2 = cap.read()
        
        if not (ret1 and ret2):
            continue
        
        # Convert to RGB for display
        frame1_rgb = cv2.cvtColor(frame1, cv2.COLOR_BGR2RGB)
        frame2_rgb = cv2.cvtColor(frame2, cv2.COLOR_BGR2RGB)
        
        # Calculate difference
        diff = np.abs(frame1.astype(float) - frame2.astype(float)).mean(axis=2)
        
        # Display
        axes[i, 0].imshow(frame1_rgb)
        axes[i, 0].set_title(f'Frame {frame_idx}')
        axes[i, 0].axis('off')
        
        axes[i, 1].imshow(frame2_rgb)
        axes[i, 1].set_title(f'Frame {frame_idx + 1}')
        axes[i, 1].axis('off')
        
        im = axes[i, 2].imshow(diff, cmap='hot')
        axes[i, 2].set_title('Difference Heatmap')
        axes[i, 2].axis('off')
        plt.colorbar(im, ax=axes[i, 2])
    
    cap.release()
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def detect_temporal_inconsistency(video_path: str,
                                  output_dir: str,
                                  sample_rate: int = 1,
                                  anomaly_sensitivity: float = 2.0,
                                  progress_callback=None) -> Dict:
    """
    Main function to detect temporal inconsistencies in video
    
    Args:
        video_path: Path to input video file
        output_dir: Directory to save output files
        sample_rate: Analyze every Nth frame (1 = all frames)
        anomaly_sensitivity: Sensitivity for anomaly detection (std multiplier)
        
    Returns:
        Dictionary containing detection results
    """
    print(f"[INFO] Analyzing video: {video_path}")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    anomaly_dir = output_path / "anomaly_frames"
    anomaly_dir.mkdir(exist_ok=True)
    
    # Open video
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    print(f"[INFO] Video info: {total_frames} frames @ {fps:.2f} FPS")
    print(f"[INFO] Computing temporal metrics...")
    
    # Read first frame
    ret, prev_frame = cap.read()
    if not ret:
        raise ValueError(f"Cannot read video: {video_path}")
    
    metrics = []
    frame_count = 0
    
    # Analyze consecutive frames
    while True:
        ret, curr_frame = cap.read()
        if not ret:
            break
        
        frame_count += 1
        
        # Sample frames if needed
        if frame_count % sample_rate != 0:
            prev_frame = curr_frame
            continue
        
        # Calculate metrics
        frame_metrics = calculate_frame_metrics(prev_frame, curr_frame)
        frame_metrics['frame_index'] = frame_count
        metrics.append(frame_metrics)
        
        prev_frame = curr_frame
        
        if frame_count % 100 == 0:
            print(f"[INFO] Processed {frame_count}/{total_frames} frames...")
        if progress_callback and frame_count % max(1, sample_rate*5) == 0:
            try:
                # Estimate local progress in [25..75]
                local_pct = 25 + int(50 * (frame_count / max(1, total_frames)))
                progress_callback(local_pct, f"Temporal analysis processed {frame_count}/{total_frames} frames")
            except Exception:
                pass
    
    cap.release()
    
    print(f"[INFO] Detecting anomalies...")
    
    # Detect anomalies
    anomaly_indices = detect_anomalies(metrics, anomaly_sensitivity)
    
    # Calculate statistics
    ssim_values = [m['ssim'] for m in metrics]
    psnr_values = [m['psnr'] for m in metrics]
    
    avg_ssim = float(np.mean(ssim_values))
    std_ssim = float(np.std(ssim_values))
    avg_psnr = float(np.mean(psnr_values))
    std_psnr = float(np.std(psnr_values))
    
    # Calculate anomaly score (0-1, higher = more suspicious)
    anomaly_ratio = len(anomaly_indices) / len(metrics) if metrics else 0
    consistency_score = avg_ssim  # Higher SSIM = more consistent
    anomaly_score = anomaly_ratio * 0.6 + (1 - consistency_score) * 0.4
    
    # Determine if manipulated (threshold: 0.3)
    is_manipulated = anomaly_score > 0.3
    
    print(f"[INFO] Generating visualizations...")
    
    # Generate temporal plot
    plot_path = output_path / "temporal_plot.png"
    generate_temporal_plot(metrics, anomaly_indices, str(plot_path))
    
    # Generate difference heatmap
    heatmap_path = output_path / "temporal_heatmap.png"
    generate_difference_heatmap(video_path, anomaly_indices, str(heatmap_path))
    
    # Save anomaly frames
    if anomaly_indices:
        cap = cv2.VideoCapture(video_path)
        for idx in anomaly_indices[:20]:  # Limit to first 20 anomalies
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if ret:
                frame_path = anomaly_dir / f"anomaly_frame_{idx:04d}.png"
                cv2.imwrite(str(frame_path), frame)
        cap.release()
    
    # Prepare results
    results = {
        'video_path': video_path,
        'total_frames': total_frames,
        'analyzed_frames': len(metrics),
        'fps': float(fps),
        'avg_ssim': avg_ssim,
        'std_ssim': std_ssim,
        'avg_psnr': avg_psnr,
        'std_psnr': std_psnr,
        'anomaly_score': float(anomaly_score),
        'is_manipulated': bool(is_manipulated),
        'num_anomalies': len(anomaly_indices),
        'anomaly_frames': anomaly_indices,
        'temporal_metrics': metrics
    }
    
    # Save JSON results
    json_path = output_path / "temporal_results.json"
    with open(json_path, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"[INFO] Anomaly score: {anomaly_score:.3f}")
    print(f"[INFO] Is manipulated: {is_manipulated}")
    print(f"[INFO] Anomalies detected: {len(anomaly_indices)}")
    print(f"[INFO] Results saved to: {output_dir}")
    
    return results


def main():
    """
    Command line interface
    
    Usage:
        python temporal_inconsistency.py <input_video> <output_dir> [sample_rate] [sensitivity]
    """
    if len(sys.argv) < 3:
        print("Usage: python temporal_inconsistency.py <input_video> <output_dir> [sample_rate] [sensitivity]")
        print("\nExample:")
        print("  python temporal_inconsistency.py video.mp4 results/")
        print("  python temporal_inconsistency.py video.mp4 results/ 2 2.5")
        print("\nParameters:")
        print("  sample_rate: Analyze every Nth frame (default: 1)")
        print("  sensitivity: Anomaly detection sensitivity (default: 2.0)")
        sys.exit(1)
    
    video_path = sys.argv[1]
    output_dir = sys.argv[2]
    sample_rate = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    sensitivity = float(sys.argv[4]) if len(sys.argv) > 4 else 2.0
    
    # Run detection
    results = detect_temporal_inconsistency(
        video_path,
        output_dir,
        sample_rate=sample_rate,
        anomaly_sensitivity=sensitivity
    )
    
    print("\n" + "="*60)
    print("TEMPORAL INCONSISTENCY DETECTION COMPLETE")
    print("="*60)
    print(f"Anomaly Score: {results['anomaly_score']:.3f}")
    print(f"Verdict: {'MANIPULATED' if results['is_manipulated'] else 'AUTHENTIC'}")
    print(f"Detected Anomalies: {results['num_anomalies']}/{results['analyzed_frames']}")
    print(f"Avg SSIM: {results['avg_ssim']:.3f} ± {results['std_ssim']:.3f}")
    print(f"Avg PSNR: {results['avg_psnr']:.1f} ± {results['std_psnr']:.1f} dB")
    print("="*60)


if __name__ == "__main__":
    main()
