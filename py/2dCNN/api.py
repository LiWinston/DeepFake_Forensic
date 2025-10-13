"""
Deep Fake Detection API
Comprehensive Flask API for AI Art vs Real Art classification
"""

import os
import torch
import tempfile
from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
from PIL import Image
import traceback
from datetime import datetime

from api_utils import (
    download_image_from_url, extract_video_frames, download_video_from_url,
    get_image_transforms, predict_single_image, predict_multiple_images,
    save_prediction_result, format_api_response, validate_file_upload,
    cleanup_temp_files, is_valid_url, TinyCNN, NanoCNN
)

app = Flask(__name__)
# Max upload size: 100MB (align with error handler below)
app.config['MAX_CONTENT_LENGTH'] = 100 * 1024 * 1024

# GPU optimization configuration
# Device detection: MPS (Apple Silicon) > CUDA (NVIDIA) > CPU
if torch.backends.mps.is_available():
    device = torch.device("mps")
    print("GPU detected: Apple Silicon (MPS)")
elif torch.cuda.is_available():
    device = torch.device("cuda")
    torch.backends.cudnn.benchmark = True  # Enable cuDNN auto-tuner for better performance
    torch.backends.cudnn.enabled = True
    print(f"GPU detected: {torch.cuda.get_device_name(0)}")
    print(f"CUDA version: {torch.version.cuda}")
else:
    device = torch.device("cpu")
    print("Warning: No GPU detected, running on CPU")

models = {}
transforms_dict = {}
model_info = {}

def load_models():
    global models, transforms_dict, model_info

    # Determine candidate model directories (env override first)
    here = os.path.dirname(os.path.abspath(__file__))
    env_dir = os.environ.get('MODEL_DIR')
    candidates = []
    if env_dir:
        candidates.append(env_dir)
    candidates.extend([
        os.path.join(here, 'models'),
        here,  # allow placing *.pth directly under py/2dCNN/
    ])

    # Gather model files from all existing candidate directories
    searched = []
    model_paths = []
    for d in candidates:
        if d and os.path.isdir(d):
            searched.append(d)
            for f in os.listdir(d):
                if f.endswith('.pth'):
                    model_paths.append(os.path.join(d, f))

    if not model_paths:
        print("Warning: No model files (.pth) found. You can set MODEL_DIR or place models under:")
        for d in candidates:
            print(f" - {d}")
        return

    transforms_dict = get_image_transforms()
    print(f"Found {len(model_paths)} model files from: {searched}")
    for model_path in model_paths:
        model_file = os.path.basename(model_path)
        try:
            if 'tiny' in model_file.lower():
                model = TinyCNN(num_classes=2).to(device)
                transform_key = 'tiny'
                model_type = 'TinyCNN'
            elif 'nano' in model_file.lower():
                model = NanoCNN(num_classes=2).to(device)
                transform_key = 'nano'
                model_type = 'NanoCNN'
            else:
                # Skip unknown model types for now
                # Add ResNet50 and ViT loading here
                continue            
            state = torch.load(model_path, map_location=device)
            # Allow older/newer checkpoints with partial key mismatch
            missing, unexpected = model.load_state_dict(state, strict=False)
            if missing or unexpected:
                print(f"Warning: state_dict mismatch for {model_file}. Missing: {len(missing)}, Unexpected: {len(unexpected)}")
            model.eval()
            # Use half precision for faster inference on GPU
            if torch.cuda.is_available():
                model = model.half()
            model_name = model_file.replace('.pth', '')
            models[model_name] = model            
            model_info[model_name] = {
                'type': model_type,
                'transform_key': transform_key,
                'file_path': model_path,
                'parameters': sum(p.numel() for p in model.parameters())
            }
            
            # Also add short name mapping for compatibility
            if 'tiny' in model_file.lower():
                short_name = 'tiny'
            elif 'nano' in model_file.lower():
                short_name = 'nano'
            else:
                short_name = None
                
            if short_name:
                models[short_name] = model
                model_info[short_name] = model_info[model_name].copy()
                print(f"Loaded {model_type} model: {model_name} (also available as '{short_name}')")
            else:
                print(f"Loaded {model_type} model: {model_name}")
        except Exception as e:
            print(f"Error loading model {model_file} from {model_path}: {e}")
    print(f"Successfully loaded {len(models)} models")

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return format_api_response(
        success=True,
        data={
            'status': 'healthy',
            'device': str(device),
            'models_loaded': len(models),
            'available_models': list(models.keys())
        },
        message="API is running normally"
    )

@app.route('/models', methods=['GET'])
def list_models():
    """List all available models"""
    return format_api_response(
        success=True,
        data={
            'models': model_info,
            'total_models': len(models)
        },
        message="Available models retrieved successfully"
    )

@app.route('/predict/image', methods=['POST'])
def predict_image():
    """
    Predict whether an uploaded image is AI-generated or real
    Supports both file upload and URL
    """
    try:
        model_name = request.form.get('model', list(models.keys())[0] if models else None)
        if not models:
            return jsonify(format_api_response(
                success=False,
                error="No models loaded. Please load models first.",
                message="Model loading required"
            )), 500
        if model_name not in models:
            return jsonify(format_api_response(
                success=False,
                error=f"Model '{model_name}' not found. Available models: {list(models.keys())}",
                message="Invalid model selection"
            )), 400
        model = models[model_name]
        transform_key = model_info[model_name]['transform_key']
        transform = transforms_dict[transform_key]        
        image_url = request.form.get('image_url')
        if image_url:
            if not is_valid_url(image_url):
                return jsonify(format_api_response(
                    success=False,
                    error="Invalid image URL provided",
                    message="URL validation failed"
                )), 400            
            image = download_image_from_url(image_url)
            source = "url"
            source_value = image_url        
        elif 'image' in request.files:
            file = request.files['image']
            is_valid, file_type_or_error = validate_file_upload(file)
            
            if not is_valid:
                return jsonify(format_api_response(
                    success=False,
                    error=file_type_or_error,
                    message="File validation failed"
                )), 400
            
            if file_type_or_error != "image":
                return jsonify(format_api_response(
                    success=False,
                    error="Expected image file, got: " + file_type_or_error,
                    message="Wrong file type"
                )), 400            
            image = Image.open(file.stream).convert('RGB')
            source = "upload"
            source_value = file.filename
        
        else:
            return jsonify(format_api_response(
                success=False,
                error="No image provided. Use 'image' file upload or 'image_url' parameter.",
                message="Missing image input"
            )), 400        
        prediction_result = predict_single_image(
            model=model,
            image=image,
            transform=transform,
            device=device
        )        
        response_data = {
            'prediction': prediction_result,
            'model_used': {
                'name': model_name,
                'type': model_info[model_name]['type'],
                'parameters': model_info[model_name]['parameters']
            },
            'source': {
                'type': source,
                'value': source_value
            }
        }        
        try:
            result_file = save_prediction_result(response_data, "image")
            response_data['result_saved_to'] = result_file
        except Exception as e:
            print(f"Warning: Could not save result to file: {e}")
        
        return jsonify(format_api_response(
            success=True,
            data=response_data,
            message="Image prediction completed successfully"
        ))
    
    except Exception as e:
        error_msg = str(e)
        traceback.print_exc()
        return jsonify(format_api_response(
            success=False,
            error=error_msg,
            message="Error during image prediction"
        )), 500

@app.route('/predict/video', methods=['POST'])
def predict_video():
    try:
        model_name = request.form.get('model', list(models.keys())[0] if models else None)
        if not models:
            return jsonify(format_api_response(
                success=False,
                error="No models loaded. Please load models first.",
                message="Model loading required"
            )), 500
        if model_name not in models:
            return jsonify(format_api_response(
                success=False,
                error=f"Model '{model_name}' not found. Available models: {list(models.keys())}",
                message="Invalid model selection"
            )), 400
        
        model = models[model_name]
        transform_key = model_info[model_name]['transform_key']
        transform = transforms_dict[transform_key]        
        max_frames = min(20, int(request.form.get('max_frames', 10)))
        video_path = None        
        try:
            video_url = request.form.get('video_url')
            if video_url:
                if not is_valid_url(video_url):
                    return jsonify(format_api_response(
                        success=False,
                        error="Invalid video URL provided",
                        message="URL validation failed"
                    )), 400                
                video_path = download_video_from_url(video_url)
                source = "url"
                source_value = video_url            
            elif 'video' in request.files:
                file = request.files['video']
                is_valid, file_type_or_error = validate_file_upload(file)
                if not is_valid:
                    return jsonify(format_api_response(
                        success=False,
                        error=file_type_or_error,
                        message="File validation failed"
                    )), 400
                if file_type_or_error != "video":
                    return jsonify(format_api_response(
                        success=False,
                        error="Expected video file, got: " + file_type_or_error,
                        message="Wrong file type"
                    )), 400                
                with tempfile.NamedTemporaryFile(delete=False, suffix='.mp4') as tmp_file:
                    file.save(tmp_file.name)
                    video_path = tmp_file.name
                source = "upload"
                source_value = file.filename
            else:
                return jsonify(format_api_response(
                    success=False,
                    error="No video provided. Use 'video' file upload or 'video_url' parameter.",
                    message="Missing video input"
                )), 400
            
            frames = extract_video_frames(video_path, max_frames=max_frames)            
            prediction_result = predict_multiple_images(
                model=model,
                images=frames,
                transform=transform,
                device=device
            )
            response_data = {
                'prediction': prediction_result,
                'video_info': {
                    'frames_analyzed': len(frames),
                    'max_frames_requested': max_frames
                },
                'model_used': {
                    'name': model_name,
                    'type': model_info[model_name]['type'],
                    'parameters': model_info[model_name]['parameters']
                },
                'source': {
                    'type': source,
                    'value': source_value
                }
            }
            try:
                result_file = save_prediction_result(response_data, "video")
                response_data['result_saved_to'] = result_file
            except Exception as e:
                print(f"Warning: Could not save result to file: {e}")
            return jsonify(format_api_response(
                success=True,
                data=response_data,
                message="Video prediction completed successfully"
            ))
        
        finally:
            cleanup_temp_files(video_path)
    
    except Exception as e:
        error_msg = str(e)
        traceback.print_exc()
        return jsonify(format_api_response(
            success=False,
            error=error_msg,
            message="Error during video prediction"
        )), 500

@app.route('/predict/batch', methods=['POST'])
def predict_batch():
    try:
        model_name = request.form.get('model', list(models.keys())[0] if models else None)
        if not models:
            return jsonify(format_api_response(
                success=False,
                error="No models loaded. Please load models first.",
                message="Model loading required"
            )), 500
        
        if model_name not in models:
            return jsonify(format_api_response(
                success=False,
                error=f"Model '{model_name}' not found. Available models: {list(models.keys())}",
                message="Invalid model selection"
            )), 400
        
        model = models[model_name]
        transform_key = model_info[model_name]['transform_key']
        transform = transforms_dict[transform_key]
        images = []
        sources = []        
        image_urls = request.form.getlist('image_urls')
        for url in image_urls:
            if is_valid_url(url):
                try:
                    image = download_image_from_url(url)
                    images.append(image)
                    sources.append({'type': 'url', 'value': url})
                except Exception as e:
                    sources.append({'type': 'url', 'value': url, 'error': str(e)})        
        uploaded_files = request.files.getlist('images')
        for file in uploaded_files:
            is_valid, file_type_or_error = validate_file_upload(file)
            if is_valid and file_type_or_error == "image":
                try:
                    image = Image.open(file.stream).convert('RGB')
                    images.append(image)
                    sources.append({'type': 'upload', 'value': file.filename})
                except Exception as e:
                    sources.append({'type': 'upload', 'value': file.filename, 'error': str(e)})
        
        if not images:
            return jsonify(format_api_response(
                success=False,
                error="No valid images provided",
                message="No processable images found"
            )), 400        
        results = []
        for i, image in enumerate(images):
            try:
                prediction = predict_single_image(
                    model=model,
                    image=image,
                    transform=transform,
                    device=device
                )
                results.append({
                    'index': i,
                    'prediction': prediction,
                    'source': sources[i] if i < len(sources) else None,
                    'status': 'success'
                })
            except Exception as e:
                results.append({
                    'index': i,
                    'source': sources[i] if i < len(sources) else None,
                    'status': 'error',
                    'error': str(e)
                })        
        successful_predictions = [r for r in results if r['status'] == 'success']
        if successful_predictions:
            ai_count = sum(1 for r in successful_predictions if r['prediction']['predicted_class'] == 0)
            real_count = len(successful_predictions) - ai_count
            avg_confidence = sum(r['prediction']['confidence'] for r in successful_predictions) / len(successful_predictions)
        else:
            ai_count = real_count = avg_confidence = 0
        
        response_data = {
            'results': results,
            'summary': {
                'total_images': len(images),
                'successful_predictions': len(successful_predictions),
                'failed_predictions': len(results) - len(successful_predictions),
                'ai_predictions': ai_count,
                'real_predictions': real_count,
                'average_confidence': avg_confidence
            },
            'model_used': {
                'name': model_name,
                'type': model_info[model_name]['type'],
                'parameters': model_info[model_name]['parameters']
            }
        }        
        try:
            result_file = save_prediction_result(response_data, "batch")
            response_data['result_saved_to'] = result_file
        except Exception as e:
            print(f"Warning: Could not save result to file: {e}")
        
        return jsonify(format_api_response(
            success=True,
            data=response_data,
            message="Batch prediction completed successfully"
        ))
    
    except Exception as e:
        error_msg = str(e)
        traceback.print_exc()
        return jsonify(format_api_response(
            success=False,
            error=error_msg,
            message="Error during batch prediction"
        )), 500

@app.errorhandler(413)
def too_large(e):
    return jsonify(format_api_response(
        success=False,
        error="File too large. Maximum size is 100MB.",
        message="File size limit exceeded"
    )), 413

@app.errorhandler(404)
def not_found(e):
    return jsonify(format_api_response(
        success=False,
        error="Endpoint not found",
        message="The requested endpoint does not exist"
    )), 404

@app.errorhandler(405)
def method_not_allowed(e):
    return jsonify(format_api_response(
        success=False,
        error="Method not allowed",
        message="The HTTP method is not allowed for this endpoint"
    )), 405

@app.errorhandler(500)
def internal_server_error(e):
    return jsonify(format_api_response(
        success=False,
        error="Internal server error",
        message="An unexpected error occurred"
    )), 500

if __name__ == '__main__':
    print("Starting Deep Fake Detection API...")
    print(f"Device: {device}")    
    os.makedirs('api_results', exist_ok=True)    
    load_models()
    
    if not models:
        print("WARNING: No models loaded! Train some models first.")
        print("The API will still start but predictions will fail.")
    
    print(f"API ready with {len(models)} models loaded")
    print("Available endpoints:")
    print("  GET  /health - Health check")
    print("  GET  /models - List available models")
    print("  POST /predict/image - Predict single image")
    print("  POST /predict/video - Predict video frames")
    print("  POST /predict/batch - Predict multiple images")
    
    app.run(debug=True, host='0.0.0.0', port=5000)