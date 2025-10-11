"""
Video Noise Pattern Analysis for Deepfake Detection
===================================================

ALGORITHM PRINCIPLE:
-------------------
Real videos captured by physical cameras contain characteristic sensor noise
patterns (Photo Response Non-Uniformity - PRNU) that are unique to each camera
sensor. This noise is:

1. Spatially consistent (same pattern across frames)
2. Temporally stable (persistent over time)
3. Hardware-specific (unique to each sensor)

AI-generated videos lack these authentic noise patterns because:
- GAN generators produce smooth, noise-free outputs
- Even if noise is added, it's random and lacks PRNU structure
- Temporal noise consistency is difficult to maintain in generated videos

Detection approach:
1. Extract noise residuals from frames using wavelet denoising
2. Analyze noise statistical properties (distribution, variance)
3. Measure noise temporal consistency across frames
4. Detect artificial noise patterns (too uniform or too random)
5. Compare noise characteristics with authentic camera noise

Key insight: Authentic videos have structured, consistent noise patterns,
while deepfakes have either no noise or artificially added random noise.

USAGE:
------
Command line:
    python video_noise_pattern.py input_video.mp4 output_dir/

Python API:
    from video_noise_pattern import analyze_noise_pattern
    results = analyze_noise_pattern('video.mp4', 'output/')

OUTPUT:
-------
output_dir/
├── noise_visualization.png        # Extracted noise patterns
├── noise_distribution_plot.png    # Noise statistical analysis
├── noise_temporal_plot.png        # Noise consistency over time
├── noise_frames/                  # Individual noise extractions
│   ├── noise_0001.png
│   ├── noise_0010.png
│   └── ...
└── noise_pattern_results.json     # Detection results

JSON FORMAT:
{
    "video_path": "input.mp4",
    "total_frames": 200,
    "avg_noise_variance": 12.34,
    "noise_temporal_consistency": 0.82,
    "noise_distribution_score": 0.45,
    "anomaly_score": 0.58,
    "is_manipulated": true,
    "noise_characteristics": {...}
}

PARAMETERS:
-----------
- sample_frames: Number of frames to analyze (default: 30)
- noise_level: Expected noise level for wavelet denoising (default: 10)
- consistency_threshold: Threshold for temporal consistency (default: 0.7)
"""

import cv2
import numpy as np
import json
import sys
from pathlib import Path
from typing import List, Dict, Tuple
import matplotlib.pyplot as plt
from scipy import stats
from scipy.fft import fft2, fftshift


def extract_noise_residual(frame: np.ndarray, sigma: float = 10.0) -> np.ndarray:
    """
    Extract noise residual from frame using denoising
    
    Method:
    - Apply Gaussian denoising to get clean signal estimate
    - Subtract clean signal from original to get noise residual
    - The residual contains sensor noise + compression artifacts
    
    Args:
        frame: Input frame (BGR)
        sigma: Noise level parameter for denoising
        
    Returns:
        Noise residual image (grayscale)
    """
    # Convert to grayscale
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY).astype(np.float64)
    
    # Denoise using Non-Local Means (better preserves structure)
    denoised = cv2.fastNlMeansDenoising(
        gray.astype(np.uint8),
        None,
        h=sigma,
        templateWindowSize=7,
        searchWindowSize=21
    )
    
    # Calculate noise residual
    noise = gray - denoised.astype(np.float64)
    
    return noise


def analyze_noise_statistics(noise: np.ndarray) -> Dict:
    """
    Analyze statistical properties of noise residual
    
    Authentic camera noise characteristics:
    - Approximately Gaussian distribution
    - Zero mean
    - Consistent variance
    - Low kurtosis (not too peaked)
    
    Fake video noise characteristics:
    - Non-Gaussian (uniform or missing)
    - Non-zero mean (processing artifacts)
    - Irregular variance
    - High kurtosis (spiky or flat)
    
    Args:
        noise: Noise residual image
        
    Returns:
        Dictionary of statistical metrics
    """
    # Flatten noise for statistics
    noise_flat = noise.flatten()
    
    # Basic statistics
    mean = float(np.mean(noise_flat))
    variance = float(np.var(noise_flat))
    std = float(np.std(noise_flat))
    
    # Higher-order statistics
    skewness = float(stats.skew(noise_flat))
    kurtosis = float(stats.kurtosis(noise_flat))
    
    # Test for normality (Gaussian distribution)
    # Kolmogorov-Smirnov test: p-value > 0.05 suggests Gaussian
    ks_statistic, ks_pvalue = stats.kstest(
        noise_flat / (std + 1e-8),  # Normalize
        'norm'
    )
    
    # Signal-to-Noise Ratio estimate
    signal_power = variance
    snr = 10 * np.log10(signal_power + 1e-8)
    
    # Entropy (randomness measure)
    # Higher entropy = more random = more authentic
    hist, _ = np.histogram(noise_flat, bins=50, density=True)
    hist = hist + 1e-10  # Avoid log(0)
    entropy = -np.sum(hist * np.log2(hist))
    
    return {
        'mean': mean,
        'variance': variance,
        'std': std,
        'skewness': skewness,
        'kurtosis': kurtosis,
        'ks_statistic': float(ks_statistic),
        'ks_pvalue': float(ks_pvalue),
        'snr_db': float(snr),
        'entropy': float(entropy),
        'is_gaussian': bool(ks_pvalue > 0.05)
    }


def calculate_temporal_consistency(noise_list: List[np.ndarray]) -> float:
    """
    Calculate temporal consistency of noise patterns across frames
    
    Authentic camera noise shows high temporal correlation because PRNU
    is a fixed sensor property. AI-generated videos show low correlation
    because noise (if present) is added randomly.
    
    Args:
        noise_list: List of noise residual images
        
    Returns:
        Consistency score (0-1, higher = more consistent = more authentic)
    """
    if len(noise_list) < 2:
        return 0.0
    
    correlations = []
    
    # Calculate correlation between consecutive noise patterns
    for i in range(len(noise_list) - 1):
        noise1 = noise_list[i].flatten()
        noise2 = noise_list[i + 1].flatten()
        
        # Pearson correlation
        if np.std(noise1) > 1e-8 and np.std(noise2) > 1e-8:
            corr = np.corrcoef(noise1, noise2)[0, 1]
            correlations.append(corr)
    
    # Average correlation
    if correlations:
        avg_correlation = float(np.mean(correlations))
        # Convert to 0-1 score (correlation ranges from -1 to 1)
        consistency_score = (avg_correlation + 1) / 2
    else:
        consistency_score = 0.0
    
    return consistency_score


def analyze_noise_frequency(noise: np.ndarray) -> Dict:
    """
    Analyze noise in frequency domain
    
    Authentic sensor noise has characteristic frequency patterns.
    AI-generated videos often lack high-frequency noise components.
    
    Args:
        noise: Noise residual image
        
    Returns:
        Dictionary of frequency domain metrics
    """
    # Apply 2D FFT
    fft = fft2(noise)
    fft_shifted = fftshift(fft)
    magnitude = np.abs(fft_shifted)
    
    # Calculate power spectrum
    power_spectrum = magnitude ** 2
    
    # Divide spectrum into frequency bands
    h, w = noise.shape
    center_h, center_w = h // 2, w // 2
    
    # Create frequency masks
    y, x = np.ogrid[:h, :w]
    dist = np.sqrt((x - center_w)**2 + (y - center_h)**2)
    
    # Low, medium, high frequency bands
    low_freq_mask = dist < min(h, w) * 0.1
    mid_freq_mask = (dist >= min(h, w) * 0.1) & (dist < min(h, w) * 0.3)
    high_freq_mask = dist >= min(h, w) * 0.3
    
    # Calculate power in each band
    low_power = float(np.mean(power_spectrum[low_freq_mask]))
    mid_power = float(np.mean(power_spectrum[mid_freq_mask]))
    high_power = float(np.mean(power_spectrum[high_freq_mask]))
    
    # High-frequency ratio (authentic videos have more high-freq noise)
    total_power = low_power + mid_power + high_power
    if total_power > 0:
        high_freq_ratio = high_power / total_power
    else:
        high_freq_ratio = 0.0
    
    return {
        'low_freq_power': low_power,
        'mid_freq_power': mid_power,
        'high_freq_power': high_power,
        'high_freq_ratio': float(high_freq_ratio)
    }


def visualize_noise_pattern(noise: np.ndarray, title: str = "Noise Pattern") -> np.ndarray:
    """
    Create visualization of noise pattern
    
    Args:
        noise: Noise residual image
        title: Title for visualization
        
    Returns:
        Visualization image (BGR)
    """
    # Normalize noise for display
    noise_normalized = cv2.normalize(noise, None, 0, 255, cv2.NORM_MINMAX)
    noise_uint8 = noise_normalized.astype(np.uint8)
    
    # Apply colormap for better visibility
    noise_colored = cv2.applyColorMap(noise_uint8, cv2.COLORMAP_JET)
    
    return noise_colored


def generate_noise_plots(noise_stats: List[Dict],
                         output_path: str):
    """
    Generate plots showing noise characteristics over time
    
    Args:
        noise_stats: List of noise statistics for each frame
        output_path: Path to save plot
    """
    frame_indices = list(range(len(noise_stats)))
    variances = [s['variance'] for s in noise_stats]
    entropies = [s['entropy'] for s in noise_stats]
    kurtosis_values = [s['kurtosis'] for s in noise_stats]
    
    fig, axes = plt.subplots(3, 1, figsize=(12, 9))
    
    # Variance plot
    axes[0].plot(frame_indices, variances, 'b-', linewidth=1.5, label='Noise Variance')
    axes[0].axhline(y=np.mean(variances), color='r', linestyle='--',
                    label=f'Mean: {np.mean(variances):.2f}')
    axes[0].set_ylabel('Variance')
    axes[0].set_title('Noise Variance Over Time')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)
    
    # Entropy plot
    axes[1].plot(frame_indices, entropies, 'g-', linewidth=1.5, label='Noise Entropy')
    axes[1].axhline(y=np.mean(entropies), color='r', linestyle='--',
                    label=f'Mean: {np.mean(entropies):.2f}')
    axes[1].set_ylabel('Entropy (bits)')
    axes[1].set_title('Noise Randomness (Entropy) Over Time')
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)
    
    # Kurtosis plot
    axes[2].plot(frame_indices, kurtosis_values, 'm-', linewidth=1.5, label='Kurtosis')
    axes[2].axhline(y=0, color='orange', linestyle='--', label='Gaussian (0)')
    axes[2].axhline(y=np.mean(kurtosis_values), color='r', linestyle='--',
                    label=f'Mean: {np.mean(kurtosis_values):.2f}')
    axes[2].set_xlabel('Frame Index')
    axes[2].set_ylabel('Kurtosis')
    axes[2].set_title('Noise Distribution Shape Over Time')
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def generate_distribution_plot(noise_list: List[np.ndarray],
                               output_path: str):
    """
    Generate histogram showing noise distribution
    
    Args:
        noise_list: List of noise residual images
        output_path: Path to save plot
    """
    # Combine all noise samples
    all_noise = np.concatenate([n.flatten() for n in noise_list])
    
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    
    # Histogram
    axes[0].hist(all_noise, bins=100, density=True, alpha=0.7, color='blue', label='Actual')
    
    # Overlay Gaussian for comparison
    mu, sigma = np.mean(all_noise), np.std(all_noise)
    x = np.linspace(all_noise.min(), all_noise.max(), 100)
    axes[0].plot(x, stats.norm.pdf(x, mu, sigma), 'r-', linewidth=2, label='Gaussian Fit')
    axes[0].set_xlabel('Noise Value')
    axes[0].set_ylabel('Density')
    axes[0].set_title('Noise Distribution vs Gaussian')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)
    
    # Q-Q plot (Quantile-Quantile plot for normality check)
    stats.probplot(all_noise, dist="norm", plot=axes[1])
    axes[1].set_title('Q-Q Plot (Normality Test)')
    axes[1].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()


def analyze_noise_pattern(video_path: str,
                          output_dir: str,
                          sample_frames: int = 30,
                          noise_sigma: float = 10.0) -> Dict:
    """
    Main function to analyze noise patterns in video
    
    Args:
        video_path: Path to input video file
        output_dir: Directory to save output files
        sample_frames: Number of frames to analyze
        noise_sigma: Noise level parameter for denoising
        
    Returns:
        Dictionary containing analysis results
    """
    print(f"[INFO] Analyzing video: {video_path}")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    noise_frames_dir = output_path / "noise_frames"
    noise_frames_dir.mkdir(exist_ok=True)
    
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
    print(f"[INFO] Extracting noise from {len(frame_indices)} frames...")
    
    noise_list = []
    noise_stats_list = []
    freq_stats_list = []
    
    for idx in frame_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ret, frame = cap.read()
        
        if not ret:
            continue
        
        # Extract noise
        noise = extract_noise_residual(frame, noise_sigma)
        noise_list.append(noise)
        
        # Analyze noise statistics
        stats_dict = analyze_noise_statistics(noise)
        stats_dict['frame_index'] = idx
        noise_stats_list.append(stats_dict)
        
        # Analyze frequency domain
        freq_stats = analyze_noise_frequency(noise)
        freq_stats_list.append(freq_stats)
        
        # Save noise visualization for every 5th frame
        if len(noise_list) % 5 == 0:
            noise_vis = visualize_noise_pattern(noise, f"Frame {idx}")
            vis_path = noise_frames_dir / f"noise_{idx:04d}.png"
            cv2.imwrite(str(vis_path), noise_vis)
        
        if len(noise_list) % 10 == 0:
            print(f"[INFO] Processed {len(noise_list)}/{len(frame_indices)} frames...")
    
    cap.release()
    
    print(f"[INFO] Analyzing noise characteristics...")
    
    # Calculate temporal consistency
    temporal_consistency = calculate_temporal_consistency(noise_list)
    
    # Calculate average statistics
    avg_variance = float(np.mean([s['variance'] for s in noise_stats_list]))
    avg_entropy = float(np.mean([s['entropy'] for s in noise_stats_list]))
    avg_kurtosis = float(np.mean([s['kurtosis'] for s in noise_stats_list]))
    gaussian_ratio = np.mean([s['is_gaussian'] for s in noise_stats_list])
    
    # Frequency domain analysis
    avg_high_freq_ratio = float(np.mean([f['high_freq_ratio'] for f in freq_stats_list]))
    
    # Calculate anomaly score
    # Factors indicating fake:
    # - Low temporal consistency (< 0.7)
    # - Non-Gaussian distribution
    # - Low entropy (too uniform)
    # - Abnormal kurtosis (far from 0)
    # - Low high-frequency content
    
    consistency_score = temporal_consistency  # 0-1, higher = better
    gaussian_score = gaussian_ratio  # 0-1, higher = better
    entropy_score = np.clip(avg_entropy / 5.0, 0, 1)  # Normalize entropy
    kurtosis_score = 1.0 - np.clip(abs(avg_kurtosis) / 10.0, 0, 1)  # Closer to 0 = better
    freq_score = avg_high_freq_ratio * 3  # Scale up (real videos ~0.3-0.4)
    freq_score = np.clip(freq_score, 0, 1)
    
    # Combined authenticity score (higher = more authentic)
    authenticity_score = (
        consistency_score * 0.3 +
        gaussian_score * 0.2 +
        entropy_score * 0.2 +
        kurtosis_score * 0.15 +
        freq_score * 0.15
    )
    
    # Anomaly score (invert authenticity)
    anomaly_score = 1.0 - authenticity_score
    
    # Determine if manipulated (threshold: 0.5)
    is_manipulated = anomaly_score > 0.5
    
    print(f"[INFO] Generating visualizations...")
    
    # Generate temporal plot
    temporal_plot_path = output_path / "noise_temporal_plot.png"
    generate_noise_plots(noise_stats_list, str(temporal_plot_path))
    
    # Generate distribution plot
    dist_plot_path = output_path / "noise_distribution_plot.png"
    generate_distribution_plot(noise_list, str(dist_plot_path))
    
    # Generate noise visualization (first frame)
    if noise_list:
        noise_vis = visualize_noise_pattern(noise_list[0], "Noise Pattern Sample")
        vis_path = output_path / "noise_visualization.png"
        cv2.imwrite(str(vis_path), noise_vis)
    
    # Prepare results
    results = {
        'video_path': video_path,
        'total_frames': total_frames,
        'analyzed_frames': len(noise_list),
        'fps': float(fps),
        'avg_noise_variance': avg_variance,
        'avg_noise_entropy': avg_entropy,
        'avg_kurtosis': avg_kurtosis,
        'gaussian_ratio': float(gaussian_ratio),
        'temporal_consistency': float(temporal_consistency),
        'avg_high_freq_ratio': avg_high_freq_ratio,
        'authenticity_score': float(authenticity_score),
        'anomaly_score': float(anomaly_score),
        'is_manipulated': bool(is_manipulated),
        'noise_characteristics': {
            'consistency_score': float(consistency_score),
            'gaussian_score': float(gaussian_score),
            'entropy_score': float(entropy_score),
            'kurtosis_score': float(kurtosis_score),
            'frequency_score': float(freq_score)
        },
        'frame_statistics': noise_stats_list
    }
    
    # Save JSON results
    json_path = output_path / "noise_pattern_results.json"
    with open(json_path, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"[INFO] Anomaly score: {anomaly_score:.3f}")
    print(f"[INFO] Authenticity score: {authenticity_score:.3f}")
    print(f"[INFO] Is manipulated: {is_manipulated}")
    print(f"[INFO] Temporal consistency: {temporal_consistency:.3f}")
    print(f"[INFO] Results saved to: {output_dir}")
    
    return results


def main():
    """
    Command line interface
    
    Usage:
        python video_noise_pattern.py <input_video> <output_dir> [sample_frames] [noise_sigma]
    """
    if len(sys.argv) < 3:
        print("Usage: python video_noise_pattern.py <input_video> <output_dir> [sample_frames] [noise_sigma]")
        print("\nExample:")
        print("  python video_noise_pattern.py video.mp4 results/")
        print("  python video_noise_pattern.py video.mp4 results/ 30 10")
        print("\nParameters:")
        print("  sample_frames: Number of frames to analyze (default: 30)")
        print("  noise_sigma: Noise level for denoising (default: 10)")
        sys.exit(1)
    
    video_path = sys.argv[1]
    output_dir = sys.argv[2]
    sample_frames = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    noise_sigma = float(sys.argv[4]) if len(sys.argv) > 4 else 10.0
    
    # Run analysis
    results = analyze_noise_pattern(
        video_path,
        output_dir,
        sample_frames=sample_frames,
        noise_sigma=noise_sigma
    )
    
    print("\n" + "="*60)
    print("NOISE PATTERN ANALYSIS COMPLETE")
    print("="*60)
    print(f"Anomaly Score: {results['anomaly_score']:.3f}")
    print(f"Authenticity Score: {results['authenticity_score']:.3f}")
    print(f"Verdict: {'MANIPULATED' if results['is_manipulated'] else 'AUTHENTIC'}")
    print(f"Temporal Consistency: {results['temporal_consistency']:.3f}")
    print(f"Gaussian Ratio: {results['gaussian_ratio']:.2%}")
    print(f"Avg Entropy: {results['avg_noise_entropy']:.2f} bits")
    print("="*60)


if __name__ == "__main__":
    main()
