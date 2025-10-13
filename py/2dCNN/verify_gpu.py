"""
Quick GPU verification script for 2D CNN implementation
Run this to verify GPU acceleration is working
"""
import torch
import time
import sys

def check_gpu_availability():
    """Check if GPU is available and display info"""
    print("=" * 60)
    print("GPU Availability Check")
    print("=" * 60)
    
    mps_available = torch.backends.mps.is_available()
    cuda_available = torch.cuda.is_available()
    
    print(f"MPS (Apple Silicon) Available: {mps_available}")
    print(f"CUDA (NVIDIA) Available: {cuda_available}")
    
    if mps_available:
        print("\n🍎 Apple Silicon GPU detected (MPS)")
        print("Note: MPS does not provide detailed memory/device info like CUDA")
        return True
    elif cuda_available:
        print(f"\nGPU Device Name: {torch.cuda.get_device_name(0)}")
        print(f"GPU Count: {torch.cuda.device_count()}")
        print(f"CUDA Version: {torch.version.cuda}")
        print(f"cuDNN Version: {torch.backends.cudnn.version()}")
        print(f"cuDNN Enabled: {torch.backends.cudnn.enabled}")
        
        # Memory info
        mem_total = torch.cuda.get_device_properties(0).total_memory / 1024**3
        mem_allocated = torch.cuda.memory_allocated(0) / 1024**3
        mem_reserved = torch.cuda.memory_reserved(0) / 1024**3
        print(f"GPU Memory - Total: {mem_total:.2f} GB")
        print(f"GPU Memory - Allocated: {mem_allocated:.4f} GB")
        print(f"GPU Memory - Reserved: {mem_reserved:.4f} GB")
        return True
    else:
        print("\nWARNING: No GPU detected. Running on CPU.")
        print("\nTo install PyTorch:")
        print("  - For CUDA support: pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118")
        print("  - For Apple Silicon: pip install torch torchvision")
        return False

def benchmark_inference(use_gpu=True):
    """Benchmark inference speed on CPU vs GPU"""
    print("\n" + "=" * 60)
    print(f"Inference Benchmark ({'GPU' if use_gpu else 'CPU'})")
    print("=" * 60)
    
    # Device detection: MPS > CUDA > CPU
    if use_gpu and torch.backends.mps.is_available():
        device = torch.device("mps")
    elif use_gpu and torch.cuda.is_available():
        device = torch.device("cuda")
    else:
        device = torch.device("cpu")
    print(f"Device: {device}")
    
    # Create a simple model
    from api_utils import TinyCNN
    model = TinyCNN(num_classes=2).to(device)
    model.eval()
    
    # Enable FP16 on CUDA GPU (MPS doesn't support .half() well)
    is_cuda = use_gpu and torch.cuda.is_available()
    if is_cuda:
        model = model.half()
        print("Model Precision: FP16")
    else:
        print("Model Precision: FP32")
    
    # Create dummy input (batch of 8 images, 64x64)
    batch_size = 8
    dummy_input = torch.randn(batch_size, 3, 64, 64).to(device)
    if is_cuda:
        dummy_input = dummy_input.half()
    
    # Warm up
    with torch.no_grad():
        for _ in range(5):
            _ = model(dummy_input)
    
    # Benchmark
    num_iterations = 100
    start_time = time.time()
    
    with torch.no_grad():
        if is_cuda:
            with torch.cuda.amp.autocast():
                for _ in range(num_iterations):
                    _ = model(dummy_input)
        else:
            for _ in range(num_iterations):
                _ = model(dummy_input)
    
    if is_cuda:
        torch.cuda.synchronize()  # Wait for GPU operations to complete
    
    end_time = time.time()
    elapsed_time = end_time - start_time
    avg_time = elapsed_time / num_iterations * 1000  # Convert to ms
    throughput = (num_iterations * batch_size) / elapsed_time
    
    print(f"Total Time: {elapsed_time:.4f} seconds")
    print(f"Average Time per Batch: {avg_time:.2f} ms")
    print(f"Throughput: {throughput:.2f} images/second")
    
    return avg_time, throughput

def main():
    """Main verification function"""
    print("\n" + "=" * 60)
    print("2D CNN GPU Verification Script")
    print("=" * 60 + "\n")
    
    # Check GPU availability
    gpu_available = check_gpu_availability()
    
    # Benchmark CPU
    print("\n" + "-" * 60)
    cpu_time, cpu_throughput = benchmark_inference(use_gpu=False)
    
    if gpu_available:
        # Benchmark GPU
        print("\n" + "-" * 60)
        gpu_time, gpu_throughput = benchmark_inference(use_gpu=True)
        
        # Compare
        print("\n" + "=" * 60)
        print("Performance Comparison")
        print("=" * 60)
        speedup = cpu_time / gpu_time
        print(f"CPU Time: {cpu_time:.2f} ms/batch")
        print(f"GPU Time: {gpu_time:.2f} ms/batch")
        print(f"Speedup: {speedup:.2f}x faster on GPU")
        print(f"CPU Throughput: {cpu_throughput:.2f} images/sec")
        print(f"GPU Throughput: {gpu_throughput:.2f} images/sec")
        
        if speedup < 1.5:
            print("\n⚠️  WARNING: GPU speedup is lower than expected.")
            print("Possible reasons:")
            print("  1. Small model size (TinyCNN is very small)")
            print("  2. GPU not fully utilized")
            print("  3. Data transfer overhead")
            print("  4. cuDNN not optimized yet (try running training first)")
        else:
            print("\n✓ GPU acceleration is working properly!")
    else:
        print("\n" + "=" * 60)
        print("GPU not available - running on CPU only")
        print("=" * 60)
    
    print("\n" + "=" * 60)
    print("Verification Complete")
    print("=" * 60)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\nError during verification: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
