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
    
    cuda_available = torch.cuda.is_available()
    print(f"CUDA Available: {cuda_available}")
    
    if cuda_available:
        print(f"GPU Device Name: {torch.cuda.get_device_name(0)}")
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
        print("WARNING: CUDA is not available. Running on CPU.")
        print("\nTo install PyTorch with CUDA support, run:")
        print("pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118")
        return False

def benchmark_inference(use_gpu=True):
    """Benchmark inference speed on CPU vs GPU"""
    print("\n" + "=" * 60)
    print(f"Inference Benchmark ({'GPU' if use_gpu else 'CPU'})")
    print("=" * 60)
    
    device = torch.device('cuda' if use_gpu and torch.cuda.is_available() else 'cpu')
    print(f"Device: {device}")
    
    # Create a simple model
    from api_utils import TinyCNN
    model = TinyCNN(num_classes=2).to(device)
    model.eval()
    
    # Enable FP16 on GPU
    if use_gpu and torch.cuda.is_available():
        model = model.half()
        print("Model Precision: FP16")
    else:
        print("Model Precision: FP32")
    
    # Create dummy input (batch of 8 images, 64x64)
    batch_size = 8
    dummy_input = torch.randn(batch_size, 3, 64, 64).to(device)
    if use_gpu and torch.cuda.is_available():
        dummy_input = dummy_input.half()
    
    # Warm up
    with torch.no_grad():
        for _ in range(5):
            _ = model(dummy_input)
    
    # Benchmark
    num_iterations = 100
    start_time = time.time()
    
    with torch.no_grad():
        if use_gpu and torch.cuda.is_available():
            with torch.cuda.amp.autocast():
                for _ in range(num_iterations):
                    _ = model(dummy_input)
        else:
            for _ in range(num_iterations):
                _ = model(dummy_input)
    
    if use_gpu and torch.cuda.is_available():
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
