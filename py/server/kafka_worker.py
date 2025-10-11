import os
import sys
import json
import time
import threading
from datetime import datetime

# Path adjustments for imports
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if os.path.join(BASE_DIR, '2dCNN') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, '2dCNN'))
if os.path.join(BASE_DIR, 'VidTraditional') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, 'VidTraditional'))

# Import analyzers
from api import load_models, models, model_info, get_image_transforms, predict_single_image, predict_multiple_images  # type: ignore
from video_noise_pattern import analyze_noise_pattern  # type: ignore
from api_utils import download_video_from_url  # type: ignore

# Kafka & Redis
from kafka import KafkaConsumer, KafkaProducer
import redis
from PIL import Image
import io

KAFKA_BOOTSTRAP = os.environ.get('KAFKA_BOOTSTRAP', 'localhost:9092')
GROUP_ID = os.environ.get('KAFKA_GROUP_ID', 'py-analyzer-group')

TOPIC_IMAGE_AI = os.environ.get('TOPIC_IMAGE_AI', 'image-ai-analysis-tasks')
TOPIC_VIDEO_TRAD = os.environ.get('TOPIC_VIDEO_TRAD', 'video-traditional-analysis-tasks')
TOPIC_VIDEO_AI = os.environ.get('TOPIC_VIDEO_AI', 'video-ai-analysis-tasks')
RESULT_TOPIC = os.environ.get('RESULT_TOPIC', 'analysis-results')

REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', '6379'))

rds = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)

PROGRESS_TTL = 3600  # seconds


def update_progress(task_id: str, percent: int, message: str = ''):
    key = f"analysis:progress:{task_id}"
    payload = {
        'progress': percent,
        'message': message,
        'timestamp': datetime.utcnow().isoformat() + 'Z'
    }
    rds.hset(key, mapping=payload)
    rds.expire(key, PROGRESS_TTL)


def process_image_ai(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    model_name = task.get('model')
    image_bytes_b64 = task.get('imageBytes')
    image_url = task.get('imageUrl')

    update_progress(task_id, 5, 'Starting image AI analysis')
    if not models:
        load_models()
    if not model_name:
        model_name = list(models.keys())[0]
    if model_name not in models:
        raise ValueError(f"Model {model_name} not found")

    model = models[model_name]
    transform_key = model_info[model_name]['transform_key']
    transforms = get_image_transforms()
    transform = transforms[transform_key]

    img = None
    if image_bytes_b64:
        import base64
        img = Image.open(io.BytesIO(base64.b64decode(image_bytes_b64))).convert('RGB')
    elif image_url:
        from api_utils import download_image_from_url
        img = download_image_from_url(image_url)
    else:
        raise ValueError('No image content provided')

    update_progress(task_id, 50, 'Running inference')
    import torch
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    result = predict_single_image(model=model, image=img, transform=transform, device=device)

    update_progress(task_id, 95, 'Inference completed')
    return {
        'taskId': task_id,
        'type': 'IMAGE_AI',
        'model': model_name,
        'result': result
    }


def process_video_traditional(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    video_path = task.get('localPath')
    url = task.get('url') or task.get('minioUrl')
    sample_frames = int(task.get('sampleFrames', 30))
    noise_sigma = float(task.get('noiseSigma', 10.0))

    if (not video_path or not os.path.exists(video_path)) and url:
        # download to temp
        try:
            video_path = download_video_from_url(url)
        except Exception as e:
            raise ValueError(f'Failed to download video from url: {e}')
    if not video_path or not os.path.exists(video_path):
        raise ValueError('localPath or downloadable url is required')

    tmp_out = os.path.join('tmp_py', task_id)
    os.makedirs(tmp_out, exist_ok=True)

    update_progress(task_id, 10, 'Extracting noise patterns')
    results = analyze_noise_pattern(video_path, tmp_out, sample_frames=sample_frames, noise_sigma=noise_sigma)

    update_progress(task_id, 95, 'Video analysis completed')
    return {
        'taskId': task_id,
        'type': 'VIDEO_TRADITIONAL_NOISE',
        'artifacts': {
            'temporal_plot': os.path.join(tmp_out, 'noise_temporal_plot.png'),
            'distribution_plot': os.path.join(tmp_out, 'noise_distribution_plot.png'),
            'visualization': os.path.join(tmp_out, 'noise_visualization.png')
        },
        'result': results
    }


def worker_loop():
    consumer = KafkaConsumer(
        TOPIC_IMAGE_AI, TOPIC_VIDEO_TRAD, TOPIC_VIDEO_AI,
        bootstrap_servers=KAFKA_BOOTSTRAP,
        group_id=GROUP_ID,
        value_deserializer=lambda m: json.loads(m.decode('utf-8')),
        key_deserializer=lambda m: m.decode('utf-8') if m else None,
        enable_auto_commit=True,
        auto_offset_reset='earliest'
    )
    producer = KafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP,
                             value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                             key_serializer=lambda v: v.encode('utf-8') if v else None)

    for msg in consumer:
        task = msg.value
        task_type = task.get('type')
        task_id = task.get('taskId') or task.get('fileMd5')
        try:
            if task_type == 'IMAGE_AI':
                result = process_image_ai(task)
            elif task_type == 'VIDEO_TRADITIONAL_NOISE':
                result = process_video_traditional(task)
            elif task_type == 'VIDEO_AI':
                # Placeholder for future video AI
                update_progress(task_id, 20, 'Video AI queued')
                time.sleep(1)
                result = { 'taskId': task_id, 'type': 'VIDEO_AI', 'status': 'NOT_IMPLEMENTED' }
            else:
                raise ValueError(f'Unknown task type: {task_type}')
            # Publish result
            producer.send(RESULT_TOPIC, key=task_id, value={ 'success': True, 'data': result })
            update_progress(task_id, 100, 'Completed')
        except Exception as e:
            producer.send(RESULT_TOPIC, key=task_id, value={ 'success': False, 'error': str(e), 'task': task })
            update_progress(task_id, 100, f'Failed: {e}')


if __name__ == '__main__':
    # Load models once
    try:
        load_models()
    except Exception as e:
        print('Model loading warning:', e)
    print('Starting Kafka worker...')
    worker_loop()
