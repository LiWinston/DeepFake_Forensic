"""
Utility functions for the Deep Fake Detection API
"""
import os
import cv2
import torch
import requests
import numpy as np
from PIL import Image
import torch.nn.functional as F
from torchvision import transforms
from urllib.parse import urlparse
import tempfile
import json
from datetime import datetime


def is_valid_url(url):
    """Check if the provided string is a valid URL"""
    try:
        result = urlparse(url)
        return all([result.scheme, result.netloc])
    except:
        return False


def download_image_from_url(url, timeout=30):
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }
        response = requests.get(url, headers=headers, timeout=timeout, stream=True)
        response.raise_for_status()        
        content_type = response.headers.get('content-type', '')
        if not content_type.startswith('image/'):
            raise ValueError(f"URL does not point to an image. Content-Type: {content_type}")        
        with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as tmp_file:
            for chunk in response.iter_content(chunk_size=8192):
                tmp_file.write(chunk)
            tmp_path = tmp_file.name
        image = Image.open(tmp_path).convert('RGB')
        os.unlink(tmp_path) 
        return image
    except requests.exceptions.RequestException as e:
        raise Exception(f"Failed to download image from URL: {str(e)}")
    except Exception as e:
        raise Exception(f"Error processing image from URL: {str(e)}")

def extract_video_frames(video_path, max_frames=10, frame_interval=30):
    try:
        cap = cv2.VideoCapture(video_path)
        frames = []
        frame_count = 0
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames <= max_frames:
            interval = 1
        else:
            interval = max(1, total_frames // max_frames)
        while cap.isOpened() and len(frames) < max_frames:
            ret, frame = cap.read()
            if not ret:
                break
            if frame_count % interval == 0:
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                pil_image = Image.fromarray(frame_rgb)
                frames.append(pil_image)
            frame_count += 1
        cap.release()
        if not frames:
            raise ValueError("No frames could be extracted from the video")
        return frames
    except Exception as e:
        raise Exception(f"Error extracting frames from video: {str(e)}")


def download_video_from_url(url, timeout=60):
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }
        response = requests.get(url, headers=headers, timeout=timeout, stream=True)
        response.raise_for_status()        
        with tempfile.NamedTemporaryFile(delete=False, suffix='.mp4') as tmp_file:
            for chunk in response.iter_content(chunk_size=8192):
                tmp_file.write(chunk)
            tmp_path = tmp_file.name
        return tmp_path
    except requests.exceptions.RequestException as e:
        raise Exception(f"Failed to download video from URL: {str(e)}")
    except Exception as e:
        raise Exception(f"Error processing video from URL: {str(e)}")


def get_image_transforms():
    standard_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])    
    tiny_transform = transforms.Compose([
        transforms.Resize((64, 64)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    nano_transform = transforms.Compose([
        transforms.Resize((32, 32)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    return {
        'standard': standard_transform,
        'tiny': tiny_transform,
        'nano': nano_transform
    }


def predict_single_image(model, image, transform, device, class_names=['AI Art', 'Real Art']):
    try:
        if image.mode != 'RGB':
            image = image.convert('RGB')
        image_tensor = transform(image).unsqueeze(0).to(device)
        model.eval()
        with torch.no_grad():
            output = model(image_tensor)
            probabilities = F.softmax(output, dim=1)[0].cpu().numpy()
            predicted_class = torch.argmax(output, dim=1).item()
            confidence = float(probabilities[predicted_class])
        return {
            'predicted_class': predicted_class,
            'predicted_label': class_names[predicted_class],
            'confidence': confidence,
            'probabilities': {
                class_names[i]: float(prob) for i, prob in enumerate(probabilities)
            }
        }
    except Exception as e:
        raise Exception(f"Error making prediction: {str(e)}")


def predict_multiple_images(model, images, transform, device, class_names=['AI Art', 'Real Art']):
    try:
        results = []
        for i, image in enumerate(images):
            prediction = predict_single_image(model, image, transform, device, class_names)
            prediction['frame_number'] = i
            results.append(prediction)
        ai_predictions = sum(1 for r in results if r['predicted_class'] == 0)
        real_predictions = sum(1 for r in results if r['predicted_class'] == 1)
        total_frames = len(results)
        overall_confidence = np.mean([r['confidence'] for r in results])
        overall_prediction = 0 if ai_predictions > real_predictions else 1
        overall_label = class_names[overall_prediction]
        return {
            'frame_predictions': results,
            'overall_prediction': {
                'predicted_class': overall_prediction,
                'predicted_label': overall_label,
                'confidence': float(overall_confidence),
                'ai_frames': ai_predictions,
                'real_frames': real_predictions,
                'total_frames': total_frames,
                'ai_percentage': float(ai_predictions / total_frames * 100),
                'real_percentage': float(real_predictions / total_frames * 100)
            }
        }
    except Exception as e:
        raise Exception(f"Error making predictions on multiple images: {str(e)}")


def save_prediction_result(result, result_type="image"):
    try:
        results_dir = "api_results"
        os.makedirs(results_dir, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{result_type}_prediction_{timestamp}.json"
        filepath = os.path.join(results_dir, filename)
        result_with_metadata = {
            'timestamp': timestamp,
            'result_type': result_type,
            'prediction_result': result
        }
        with open(filepath, 'w') as f:
            json.dump(result_with_metadata, f, indent=2)
        return filepath
    except Exception as e:
        raise Exception(f"Error saving prediction result: {str(e)}")


def format_api_response(success=True, data=None, message="", error=None):
    response = {
        'success': success,
        'timestamp': datetime.now().isoformat(),
        'message': message
    }
    if success:
        response['data'] = data
    else:
        response['error'] = error
    
    return response


def validate_file_upload(file):
    if not file:
        return False, "No file provided"
    
    if file.filename == '':
        return False, "No file selected"    
    allowed_image_extensions = {'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'}
    allowed_video_extensions = {'mp4', 'avi', 'mov', 'mkv', 'wmv', 'flv', 'webm'}
    
    filename = file.filename.lower()
    file_extension = filename.rsplit('.', 1)[1] if '.' in filename else ''
    
    if file_extension in allowed_image_extensions:
        return True, "image"
    elif file_extension in allowed_video_extensions:
        return True, "video"
    else:
        return False, f"Unsupported file type: {file_extension}"


def cleanup_temp_files(*file_paths):
    for path in file_paths:
        try:
            if path and os.path.exists(path):
                os.unlink(path)
        except Exception as e:
            print(f"Warning: Could not delete temporary file {path}: {e}")
import torch.nn as nn

class TinyCNN(nn.Module):
    """
    Ultra-tiny CNN for extremely fast training
    Total parameters: ~25K
    """
    def __init__(self, num_classes=2, input_size=64):
        super(TinyCNN, self).__init__()
        
        self.features = nn.Sequential(
            # Block 1: 3 -> 8
            nn.Conv2d(3, 8, kernel_size=3, stride=2, padding=1),  # 64x64 -> 32x32
            nn.BatchNorm2d(8),
            nn.ReLU(inplace=True),
            
            # Block 2: 8 -> 16
            nn.Conv2d(8, 16, kernel_size=3, stride=2, padding=1),  # 32x32 -> 16x16
            nn.BatchNorm2d(16),
            nn.ReLU(inplace=True),
            
            # Block 3: 16 -> 32
            nn.Conv2d(16, 32, kernel_size=3, stride=2, padding=1),  # 16x16 -> 8x8
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            
            # Block 4: 32 -> 64
            nn.Conv2d(32, 64, kernel_size=3, stride=2, padding=1),  # 8x8 -> 4x4
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            
            # Global pooling
            nn.AdaptiveAvgPool2d((1, 1))  # 4x4 -> 1x1
        )
        
        self.classifier = nn.Sequential(
            nn.Dropout(0.3),
            nn.Linear(64, 32),
            nn.ReLU(inplace=True),
            nn.Dropout(0.2),
            nn.Linear(32, num_classes)
        )
        
        self._initialize_weights()
    
    def forward(self, x):
        x = self.features(x)
        x = x.view(x.size(0), -1)
        x = self.classifier(x)
        return x
    
    def _initialize_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Conv2d):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
            elif isinstance(m, nn.BatchNorm2d):
                nn.init.constant_(m.weight, 1)
                nn.init.constant_(m.bias, 0)
            elif isinstance(m, nn.Linear):
                nn.init.normal_(m.weight, 0, 0.01)
                nn.init.constant_(m.bias, 0)


class NanoCNN(nn.Module):
    """
    Nano CNN for lightning-fast training
    Total parameters: ~8K
    """
    def __init__(self, num_classes=2, input_size=32):
        super(NanoCNN, self).__init__()
        
        self.features = nn.Sequential(
            # Single path with minimal channels
            nn.Conv2d(3, 8, kernel_size=5, stride=2, padding=2),  # 32x32 -> 16x16
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),  # 16x16 -> 8x8
            
            nn.Conv2d(8, 16, kernel_size=3, stride=2, padding=1),  # 8x8 -> 4x4
            nn.ReLU(inplace=True),
            
            # Global pooling
            nn.AdaptiveAvgPool2d((1, 1))
        )
        
        self.classifier = nn.Sequential(
            nn.Linear(16, num_classes)
        )
        
        self._initialize_weights()
    
    def forward(self, x):
        x = self.features(x)
        x = x.view(x.size(0), -1)
        x = self.classifier(x)
        return x
    
    def _initialize_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Conv2d):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
            elif isinstance(m, nn.Linear):
                nn.init.normal_(m.weight, 0, 0.01)
                nn.init.constant_(m.bias, 0)