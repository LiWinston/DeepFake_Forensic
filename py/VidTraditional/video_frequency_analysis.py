"""
Video Frequency Domain Analysis for Deepfake Detection
======================================================

ALGORITHM PRINCIPLE:
-------------------
GAN-generated videos have distinctive artifacts in the frequency domain that
are invisible in the spatial domain. This is because:

1. GAN generators operate with limited spectral capacity
2. Upsampling operations create periodic patterns in frequency spectrum
3. Missing high-frequency components (over-smoothing)
4. Artificial frequency peaks from checkerboard artifacts
5. Inconsistent frequency characteristics across frames

Real videos have:
- Rich high-frequency content (texture, details)
- Natural frequency distribution
- Consistent spectral patterns across frames
- No artificial periodic patterns

Detection approach:
1. Transform frames to frequency domain using 2D FFT
2. Analyze power spectrum distribution
3. Detect artificial frequency peaks and patterns
4. Measure high-frequency content ratio
5. Analyze temporal frequency consistency

Key insight: GANs leave "fingerprints" in the frequency domain that reveal
their synthetic nature, even when spatial domain appears realistic.

USAGE:
------
Command line:
    python video_frequency_analysis.py input_video.mp4 output_dir/

Python API:
    from video_frequency_analysis import analyze_frequency_domain
    results = analyze_frequency_domain('video.mp4', 'output/')

OUTPUT:
-------
output_dir/
├── frequency_spectrum.png         # Power spectrum visualization
├── frequency_analysis_plot.png    # Frequency metrics over time
├── frequency_heatmap.png          # Spatial frequency heatmap
├── frequency_frames/              # Individual frequency analysis
│   ├── freq_0001.png
│   ├── freq_0010.png
│   └── ...
└── frequency_results.json         # Detection results

JSON FORMAT:
{
    "video_path": "input.mp4",
    "total_frames": 200,
    "avg_high_freq_ratio": 0.234,
    "spectral_flatness": 0.456,
    "frequency_consistency": 0.789,
    "anomaly_score": 0.65,
    "is_manipulated": true,
    "gan_fingerprint_detected": true
}

PARAMETERS:
-----------
- sample_frames: Number of frames to analyze (default: 40)
- high_freq_threshold: Threshold for high frequency band (default: 0.5)
- consistency_window: Window size for temporal analysis (default: 5)
"""

import cv2
import numpy as np
import json
import sys
from pathlib import Path
from typing import List, Dict, Tuple
import matplotlib.pyplot as plt
from scipy.fft import fft2, fftshift, ifft2, ifftshift
from scipy.signal import find_peaks


def compute_frequency_spectrum(frame: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    """
    Compute 2D frequency spectrum of frame using Fast Fourier Transform
    
    FFT transforms spatial domain image to frequency domain, revealing:
    - Low frequencies: Overall structure, smooth regions
    - High frequencies: Details, edges, textures
    
    Args:
        frame: Input frame (BGR)
        
    Returns:
        (magnitude_spectrum, phase_spectrum)
    """
    # Convert to grayscale
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY).astype(np.float64)
    
    # Apply 2D FFT
    fft = fft2(gray)
    fft_shifted = fftshift(fft)  # Shift zero frequency to center
    
    # Calculate magnitude and phase
    magnitude = np.abs(fft_shifted)
    phase = np.angle(fft_shifted)
    
    # Log scale for better visualization
    magnitude_log = np.log1p(magnitude)
    
    return magnitude_log, phase


def analyze_frequency_bands(magnitude: np.ndarray) -> Dict:
    """
    Analyze frequency content in different bands
    
    Frequency bands:
    - Low: 0-20% radius (overall structure)
    - Mid: 20-50% radius (medium details)
    - High: 50-100% radius (fine details, edges)
    
    Fake videos typically have:
    - Higher low-frequency energy (smooth)
    - Lower high-frequency energy (missing details)
    
    Args:
        magnitude: Magnitude spectrum (log scale)
        
    Returns:
        Dictionary with frequency band statistics
    """
    h, w = magnitude.shape
    center_h, center_w = h // 2, w // 2
    
    # Create distance map from center
    y, x = np.ogrid[:h, :w]
    dist = np.sqrt((x - center_w)**2 + (y - center_h)**2)
    max_dist = np.sqrt(center_h**2 + center_w**2)
    
    # Normalize distance to [0, 1]
    dist_norm = dist / max_dist
    
    # Define frequency bands
    low_mask = dist_norm < 0.2
    mid_mask = (dist_norm >= 0.2) & (dist_norm < 0.5)
    high_mask = dist_norm >= 0.5
    
    # Calculate power in each band
    # Use exponential to convert back from log scale
    magnitude_power = np.expm1(magnitude)  # Inverse of log1p
    
    low_power = float(np.mean(magnitude_power[low_mask]))
    mid_power = float(np.mean(magnitude_power[mid_mask]))
    high_power = float(np.mean(magnitude_power[high_mask]))
    
    total_power = low_power + mid_power + high_power
    
    # Calculate ratios
    if total_power > 0:
        low_ratio = low_power / total_power
        mid_ratio = mid_power / total_power
        high_ratio = high_power / total_power
    else:
        low_ratio = mid_ratio = high_ratio = 0.0
    
    # Spectral centroid (center of mass of spectrum)
    # Lower centroid = more low-frequency = possibly fake
    if total_power > 0:
        centroid = np.sum(dist_norm * magnitude_power) / np.sum(magnitude_power)
    else:
        centroid = 0.0
    
    return {
        'low_freq_power': low_power,
        'mid_freq_power': mid_power,
        'high_freq_power': high_power,
        'low_freq_ratio': float(low_ratio),
        'mid_freq_ratio': float(mid_ratio),
        'high_freq_ratio': float(high_ratio),
        'spectral_centroid': float(centroid)
    }


def detect_periodic_artifacts(magnitude: np.ndarray) -> Dict:
    """
    Detect periodic patterns in frequency spectrum (GAN artifacts)
    
    GAN upsampling operations (e.g., transpose convolution) can create
    checkerboard patterns that appear as regular peaks in frequency domain.
    
    Args:
        magnitude: Magnitude spectrum
        
    Returns:
        Dictionary with artifact detection results
    """
    h, w = magnitude.shape
    center_h, center_w = h // 2, w // 2
    
    # Extract radial profile (average magnitude at each frequency)
    y, x = np.ogrid[:h, :w]
    dist = np.sqrt((x - center_w)**2 + (y - center_h)**2).astype(int)
    max_radius = int(np.sqrt(center_h**2 + center_w**2))
    
    radial_profile = []
    for r in range(max_radius):
        mask = (dist == r)
        if np.any(mask):
            radial_profile.append(np.mean(magnitude[mask]))
        else:
            radial_profile.append(0)
    
    radial_profile = np.array(radial_profile)
    
    # Detect peaks in radial profile (periodic artifacts show as peaks)
    if len(radial_profile) > 10:
        peaks, properties = find_peaks(
            radial_profile,
            prominence=np.std(radial_profile) * 0.5,
            distance=5
        )
        num_peaks = len(peaks)
        peak_prominence = float(np.mean(properties['prominences'])) if num_peaks > 0 else 0.0
    else:
        num_peaks = 0
        peak_prominence = 0.0
    
    # Calculate spectral flatness (Wiener entropy)
    # Measures how "flat" the spectrum is
    # High flatness = noise-like (good for real videos)
    # Low flatness = tonal (suspicious, possible GAN)
    magnitude_linear = np.expm1(magnitude)
    magnitude_flat = magnitude_linear.flatten()
    magnitude_flat = magnitude_flat[magnitude_flat > 0]
    
    if len(magnitude_flat) > 0:
        geometric_mean = np.exp(np.mean(np.log(magnitude_flat + 1e-10)))
        arithmetic_mean = np.mean(magnitude_flat)
        spectral_flatness = geometric_mean / (arithmetic_mean + 1e-10)
    else:
        spectral_flatness = 0.0
    
    return {
        'num_periodic_peaks': int(num_peaks),
        'peak_prominence': peak_prominence,
        'spectral_flatness': float(spectral_flatness),
        'has_periodic_artifacts': bool(num_peaks > 3)  # Threshold
    }


def calculate_frequency_consistency(freq_stats: List[Dict]) -> float:
    """
    Calculate temporal consistency of frequency characteristics
    
    Real videos maintain consistent frequency patterns across frames.
    AI-generated videos show inconsistent frequency distributions.
    
    Args:
        freq_stats: List of frequency statistics for each frame
        
    Returns:
        Consistency score (0-1, higher = more consistent)
    """
    if len(freq_stats) < 2:
        return 0.0
    
    # Extract high-frequency ratios
    high_freq_ratios = np.array([s['high_freq_ratio'] for s in freq_stats])
    
    # Calculate coefficient of variation (std / mean)
    # Lower CV = more consistent
    mean_ratio = np.mean(high_freq_ratios)
    std_ratio = np.std(high_freq_ratios)
    
    if mean_ratio > 0:
        cv = std_ratio / mean_ratio
        # Convert to consistency score (invert and normalize)
        consistency = 1.0 / (1.0 + cv)
    else:
        consistency = 0.0
    
    return float(consistency)


def visualize_frequency_spectrum(magnitude: np.ndarray, 
                                 title: str = "Frequency Spectrum") -> np.ndarray:
    """
    Create visualization of frequency spectrum
    
    Args:
        magnitude: Magnitude spectrum (log scale)
        title: Title for visualization
        
    Returns:
        Visualization image (BGR)
    """
    # Normalize for display
    magnitude_normalized = cv2.normalize(magnitude, None, 0, 255, cv2.NORM_MINMAX)
    magnitude_uint8 = magnitude_normalized.astype(np.uint8)
    
    # Apply colormap
    spectrum_colored = cv2.applyColorMap(magnitude_uint8, cv2.COLORMAP_JET)
    
    # Draw crosshair at center (DC component)
    h, w = spectrum_colored.shape[:2]
    cv2.line(spectrum_colored, (w//2, 0), (w//2, h), (255, 255, 255), 1)
    cv2.line(spectrum_colored, (0, h//2), (w, h//2), (255, 255, 255), 1)
    
    # Draw frequency band circles
    center = (w//2, h//2)
    max_radius = int(np.sqrt((h//2)**2 + (w//2)**2))
    cv2.circle(spectrum_colored, center, int(max_radius * 0.2), (0, 255, 0), 1)  # Low
    cv2.circle(spectrum_colored, center, int(max_radius * 0.5), (255, 255, 0), 1)  # Mid
    
    return spectrum_colored


def generate_frequency_plots(freq_stats: List[Dict],
                             artifact_stats: List[Dict],
                             output_path: str):
    """
    Generate plots showing frequency metrics over time
    
    Args:
        freq_stats: List of frequency band statistics
        artifact_stats: List of artifact detection results
        output_path: Path to save plot
    """
    frame_indices = list(range(len(freq_stats)))
    high_freq_ratios = [s['high_freq_ratio'] for s in freq_stats]
    spectral_centroids = [s['spectral_centroid'] for s in freq_stats]
    spectral_flatness = [a['spectral_flatness'] for a in artifact_stats]
    num_peaks = [a['num_periodic_peaks'] for a in artifact_stats]
    
    fig, axes = plt.subplots(4, 1, figsize=(12, 10))
    
    # High frequency ratio plot
    axes[0].plot(frame_indices, high_freq_ratios, 'b-', linewidth=1.5, label='High Freq Ratio')
    axes[0].axhline(y=np.mean(high_freq_ratios), color='r', linestyle='--',
                    label=f'Mean: {np.mean(high_freq_ratios):.3f}')
    axes[0].axhline(y=0.15, color='orange', linestyle=':', label='Typical Real: ~0.15+')
    axes[0].set_ylabel('Ratio')
    axes[0].set_title('High-Frequency Content Ratio Over Time')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)
    
    # Spectral centroid plot
    axes[1].plot(frame_indices, spectral_centroids, 'g-', linewidth=1.5, label='Spectral Centroid')
    axes[1].axhline(y=np.mean(spectral_centroids), color='r', linestyle='--',
                    label=f'Mean: {np.mean(spectral_centroids):.3f}')
    axes[1].set_ylabel('Centroid (normalized)')
    axes[1].set_title('Spectral Centroid Over Time (Higher = More High-Freq)')
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)
    
    # Spectral flatness plot
    axes[2].plot(frame_indices, spectral_flatness, 'm-', linewidth=1.5, label='Spectral Flatness')
    axes[2].axhline(y=np.mean(spectral_flatness), color='r', linestyle='--',
                    label=f'Mean: {np.mean(spectral_flatness):.3f}')
    axes[2].set_ylabel('Flatness')
    axes[2].set_title('Spectral Flatness Over Time (Higher = More Noise-like)')
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)
    
    # Periodic peaks plot
    axes[3].bar(frame_indices, num_peaks, color='red', alpha=0.7, label='Periodic Peaks')
    axes[3].axhline(y=3, color='orange', linestyle='--', label='Suspicious Threshold (3)')
    axes[3].set_xlabel('Frame Index')
    axes[3].set_ylabel('Number of Peaks')
    axes[3].set_title('Periodic Artifacts Detection (GAN Fingerprints)')
    axes[3].legend()
    axes[3].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def generate_spectrum_comparison(magnitude_list: List[np.ndarray],
                                output_path: str,
                                max_samples: int = 6):
    """
    Generate comparison of frequency spectra from different frames
    
    Args:
        magnitude_list: List of magnitude spectra
        output_path: Path to save comparison
        max_samples: Maximum number of spectra to display
    """
    num_samples = min(len(magnitude_list), max_samples)
    indices = np.linspace(0, len(magnitude_list) - 1, num_samples, dtype=int)
    
    fig, axes = plt.subplots(2, 3, figsize=(15, 10))
    axes = axes.flatten()
    
    for i, idx in enumerate(indices):
        magnitude = magnitude_list[idx]
        
        # Normalize for display
        mag_norm = cv2.normalize(magnitude, None, 0, 1, cv2.NORM_MINMAX)
        
        axes[i].imshow(mag_norm, cmap='jet')
        axes[i].set_title(f'Frame {idx}')
        axes[i].axis('off')
        
        # Draw frequency band circles
        h, w = magnitude.shape
        center = (w//2, h//2)
        max_radius = np.sqrt((h//2)**2 + (w//2)**2)
        
        circle1 = plt.Circle(center, max_radius * 0.2, fill=False, color='green', linewidth=1)
        circle2 = plt.Circle(center, max_radius * 0.5, fill=False, color='yellow', linewidth=1)
        axes[i].add_patch(circle1)
        axes[i].add_patch(circle2)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def analyze_frequency_domain(video_path: str,
                             output_dir: str,
                             sample_frames: int = 40,
                             progress_callback=None) -> Dict:
    """
    Main function to analyze frequency domain characteristics of video
    
    Args:
        video_path: Path to input video file
        output_dir: Directory to save output files
        sample_frames: Number of frames to analyze
        
    Returns:
        Dictionary containing analysis results
    """
    print(f"[INFO] Analyzing video: {video_path}")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    freq_frames_dir = output_path / "frequency_frames"
    freq_frames_dir.mkdir(exist_ok=True)
    
    # Open video
    cap = cv2.VideoCapture(video_path)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    
    # Calculate frame indices to analyze
    if total_frames < sample_frames:
        frame_indices = list(range(total_frames))
    else:
        frame_indices = np.linspace(0, total_frames - 1, sample_frames, dtype=int).tolist()
    
    print(f"[INFO] Video: {total_frames} frames @ {fps:.2f} FPS")
    print(f"[INFO] Analyzing frequency domain of {len(frame_indices)} frames...")
    
    magnitude_list = []
    freq_stats_list = []
    artifact_stats_list = []
    
    processed = 0
    for idx in frame_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ret, frame = cap.read()
        
        if not ret:
            continue
        
        # Compute frequency spectrum
        magnitude, phase = compute_frequency_spectrum(frame)
        magnitude_list.append(magnitude)
        
        # Analyze frequency bands
        freq_stats = analyze_frequency_bands(magnitude)
        freq_stats['frame_index'] = idx
        freq_stats_list.append(freq_stats)
        
        # Detect periodic artifacts
        artifact_stats = detect_periodic_artifacts(magnitude)
        artifact_stats_list.append(artifact_stats)
        
        # Save visualization for every 5th frame
        if len(magnitude_list) % 5 == 0:
            spectrum_vis = visualize_frequency_spectrum(magnitude, f"Frame {idx}")
            vis_path = freq_frames_dir / f"freq_{idx:04d}.png"
            cv2.imwrite(str(vis_path), spectrum_vis)
        
        processed += 1
        if processed % 5 == 0:
            print(f"[INFO] Processed {len(magnitude_list)}/{len(frame_indices)} frames...")
            if progress_callback:
                local_pct = 25 + int(50 * (processed / max(1, len(frame_indices))))
                try:
                    progress_callback(local_pct, f"Frequency analysis processed {processed}/{len(frame_indices)} frames")
                except Exception:
                    pass
    
    cap.release()
    
    print(f"[INFO] Computing frequency statistics...")
    
    # Calculate temporal consistency
    freq_consistency = calculate_frequency_consistency(freq_stats_list)
    
    # Calculate average statistics
    avg_high_freq_ratio = float(np.mean([s['high_freq_ratio'] for s in freq_stats_list]))
    avg_spectral_centroid = float(np.mean([s['spectral_centroid'] for s in freq_stats_list]))
    avg_spectral_flatness = float(np.mean([a['spectral_flatness'] for a in artifact_stats_list]))
    
    # GAN fingerprint detection
    frames_with_artifacts = sum(1 for a in artifact_stats_list if a['has_periodic_artifacts'])
    artifact_ratio = frames_with_artifacts / len(artifact_stats_list) if artifact_stats_list else 0
    gan_fingerprint_detected = artifact_ratio > 0.3  # More than 30% frames have artifacts
    
    # Calculate anomaly score
    # Indicators of fake:
    # - Low high-frequency ratio (< 0.15)
    # - Low spectral centroid (< 0.3)
    # - Low spectral flatness (< 0.1)
    # - Presence of periodic artifacts
    # - Low temporal consistency
    
    # Normalize scores (0-1, higher = more authentic)
    high_freq_score = np.clip(avg_high_freq_ratio / 0.2, 0, 1)  # Normalize around 0.2
    centroid_score = np.clip(avg_spectral_centroid / 0.4, 0, 1)  # Normalize around 0.4
    flatness_score = np.clip(avg_spectral_flatness / 0.2, 0, 1)  # Normalize around 0.2
    artifact_score = 1.0 - artifact_ratio  # Invert (fewer artifacts = better)
    consistency_score = freq_consistency
    
    # Combined authenticity score
    authenticity_score = (
        high_freq_score * 0.3 +
        centroid_score * 0.2 +
        flatness_score * 0.2 +
        artifact_score * 0.15 +
        consistency_score * 0.15
    )
    
    # Anomaly score (invert authenticity)
    anomaly_score = 1.0 - authenticity_score
    
    # Determine if manipulated (threshold: 0.5)
    is_manipulated = anomaly_score > 0.5
    
    print(f"[INFO] Generating visualizations...")
    
    # Generate frequency plots
    plot_path = output_path / "frequency_analysis_plot.png"
    generate_frequency_plots(freq_stats_list, artifact_stats_list, str(plot_path))
    
    # Generate spectrum comparison
    comparison_path = output_path / "frequency_spectrum.png"
    generate_spectrum_comparison(magnitude_list, str(comparison_path))
    
    # Save first frame spectrum as main visualization
    if magnitude_list:
        spectrum_vis = visualize_frequency_spectrum(magnitude_list[0], "Frequency Spectrum")
        main_vis_path = output_path / "frequency_heatmap.png"
        cv2.imwrite(str(main_vis_path), spectrum_vis)
    
    # Prepare results
    results = {
        'video_path': video_path,
        'total_frames': total_frames,
        'analyzed_frames': len(magnitude_list),
        'fps': float(fps),
        'avg_high_freq_ratio': avg_high_freq_ratio,
        'avg_spectral_centroid': avg_spectral_centroid,
        'avg_spectral_flatness': avg_spectral_flatness,
        'frequency_consistency': freq_consistency,
        'frames_with_artifacts': int(frames_with_artifacts),
        'artifact_ratio': float(artifact_ratio),
        'gan_fingerprint_detected': bool(gan_fingerprint_detected),
        'authenticity_score': float(authenticity_score),
        'anomaly_score': float(anomaly_score),
        'is_manipulated': bool(is_manipulated),
        'component_scores': {
            'high_freq_score': float(high_freq_score),
            'centroid_score': float(centroid_score),
            'flatness_score': float(flatness_score),
            'artifact_score': float(artifact_score),
            'consistency_score': float(consistency_score)
        },
        'frame_statistics': [
            {**freq_stats_list[i], **artifact_stats_list[i]}
            for i in range(len(freq_stats_list))
        ]
    }
    
    # Save JSON results
    json_path = output_path / "frequency_results.json"
    with open(json_path, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"[INFO] Anomaly score: {anomaly_score:.3f}")
    print(f"[INFO] Authenticity score: {authenticity_score:.3f}")
    print(f"[INFO] Is manipulated: {is_manipulated}")
    print(f"[INFO] GAN fingerprint detected: {gan_fingerprint_detected}")
    print(f"[INFO] High-freq ratio: {avg_high_freq_ratio:.3f}")
    print(f"[INFO] Results saved to: {output_dir}")
    
    return results


def main():
    """
    Command line interface
    
    Usage:
        python video_frequency_analysis.py <input_video> <output_dir> [sample_frames]
    """
    if len(sys.argv) < 3:
        print("Usage: python video_frequency_analysis.py <input_video> <output_dir> [sample_frames]")
        print("\nExample:")
        print("  python video_frequency_analysis.py video.mp4 results/")
        print("  python video_frequency_analysis.py video.mp4 results/ 40")
        print("\nParameters:")
        print("  sample_frames: Number of frames to analyze (default: 40)")
        sys.exit(1)
    
    video_path = sys.argv[1]
    output_dir = sys.argv[2]
    sample_frames = int(sys.argv[3]) if len(sys.argv) > 3 else 40
    
    # Run analysis
    results = analyze_frequency_domain(
        video_path,
        output_dir,
        sample_frames=sample_frames
    )
    
    print("\n" + "="*60)
    print("FREQUENCY DOMAIN ANALYSIS COMPLETE")
    print("="*60)
    print(f"Anomaly Score: {results['anomaly_score']:.3f}")
    print(f"Authenticity Score: {results['authenticity_score']:.3f}")
    print(f"Verdict: {'MANIPULATED' if results['is_manipulated'] else 'AUTHENTIC'}")
    print(f"GAN Fingerprint: {'DETECTED' if results['gan_fingerprint_detected'] else 'NOT DETECTED'}")
    print(f"High-Freq Ratio: {results['avg_high_freq_ratio']:.3f} (Real: ~0.15+)")
    print(f"Spectral Flatness: {results['avg_spectral_flatness']:.3f}")
    print(f"Artifact Frames: {results['frames_with_artifacts']}/{results['analyzed_frames']}")
    print("="*60)


if __name__ == "__main__":
    main()
