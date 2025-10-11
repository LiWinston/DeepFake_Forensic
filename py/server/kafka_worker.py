import os
import sys
import json
import time
import threading
from datetime import datetime
from dotenv import load_dotenv

load_dotenv()
# Path adjustments for imports
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if os.path.join(BASE_DIR, '2dCNN') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, '2dCNN'))
if os.path.join(BASE_DIR, 'VidTraditional') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, 'VidTraditional'))

# Import analyzers
from api import load_models, models, model_info, get_image_transforms, predict_single_image, predict_multiple_images  # type: ignore
from video_noise_pattern import analyze_noise_pattern  # type: ignore
from optical_flow_analysis import analyze_optical_flow  # type: ignore
from video_frequency_analysis import analyze_frequency_domain  # type: ignore
from temporal_inconsistency import detect_temporal_inconsistency  # type: ignore
from video_copy_move import detect_copy_move  # type: ignore
from api_utils import download_video_from_url  # type: ignore

# Kafka & Redis
from kafka import KafkaConsumer, KafkaProducer
import redis
from PIL import Image
import io

KAFKA_BOOTSTRAP = os.environ.get('KAFKA_BOOTSTRAP', 'localhost:9092')
GROUP_ID = os.environ.get('KAFKA_GROUP_ID', 'py-analyzer-group')
KAFKA_OFFSET_RESET = os.environ.get('KAFKA_OFFSET_RESET', 'latest')  # 'latest' by default to avoid replaying old messages

TOPIC_IMAGE_AI = os.environ.get('TOPIC_IMAGE_AI', 'image-ai-analysis-tasks')
TOPIC_VIDEO_TRAD = os.environ.get('TOPIC_VIDEO_TRAD', 'video-traditional-analysis-tasks')
TOPIC_VIDEO_AI = os.environ.get('TOPIC_VIDEO_AI', 'video-ai-analysis-tasks')
RESULT_TOPIC = os.environ.get('RESULT_TOPIC', 'analysis-results')

REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', '6379'))

# MinIO for artifact uploads (optional)
MINIO_ENDPOINT = os.environ.get('MINIO_ENDPOINT')
MINIO_ACCESS_KEY = os.environ.get('MINIO_ACCESS_KEY')
MINIO_SECRET_KEY = os.environ.get('MINIO_SECRET_KEY')
MINIO_BUCKET = os.environ.get('MINIO_BUCKET')
MINIO_SECURE = os.environ.get('MINIO_SECURE', 'false').lower() == 'true'
minio_client = None
if MINIO_ENDPOINT and MINIO_ACCESS_KEY and MINIO_SECRET_KEY and MINIO_BUCKET:
    try:
        from minio import Minio
        minio_client = Minio(MINIO_ENDPOINT.replace('http://', '').replace('https://', ''),
                             access_key=MINIO_ACCESS_KEY,
                             secret_key=MINIO_SECRET_KEY,
                             secure=MINIO_SECURE)
    except Exception as _e:
        print('MinIO client init failed:', _e)

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


def _ensure_video_path(task: dict, task_id: str):
    video_path = task.get('localPath')
    url = task.get('url') or task.get('minioUrl')
    if (not video_path or not os.path.exists(video_path)) and url:
        try:
            update_progress(task_id, 10, 'Downloading video')
            video_path = download_video_from_url(url)
        except Exception as e:
            update_progress(task_id, 100, f'Failed downloading video: {e}')
            raise ValueError(f'Failed to download video from url: {e}')
    if not video_path or not os.path.exists(video_path):
        raise ValueError('localPath or downloadable url is required')
    return video_path


def _upload_artifacts(task_id: str, artifacts: dict):
    uploaded = {}
    if minio_client:
        total = len(artifacts)
        done = 0
        for k, path in artifacts.items():
            try:
                if os.path.exists(path):
                    object_name = f"artifacts/{task_id}/{os.path.basename(path)}"
                    minio_client.fput_object(MINIO_BUCKET, object_name, path)
                    scheme = 'https' if MINIO_SECURE else 'http'
                    uploaded[k] = f"{scheme}://{MINIO_ENDPOINT.strip('/').replace('http://','').replace('https://','')}/{MINIO_BUCKET}/{object_name}"
                    done += 1
                    pct = 80 + int((done/total) * 15)
                    update_progress(task_id, pct, f'Uploading artifacts ({done}/{total})')
            except Exception as e:
                print('MinIO upload failed for', path, e)
    return uploaded


def process_video_traditional(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    file_md5 = task.get('fileMd5') or task_id
    video_path = None
    sample_frames = int(task.get('sampleFrames', 30))
    noise_sigma = float(task.get('noiseSigma', 10.0))

    update_progress(task_id, 5, 'Task received')
    video_path = _ensure_video_path(task, task_id)

    tmp_out = os.path.join('tmp_py', task_id)
    os.makedirs(tmp_out, exist_ok=True)

    update_progress(task_id, 20, 'Decoding & sampling frames')
    # The underlying analyzer performs frame sampling; we reflect staged progress for UX
    # We can't hook into its internals without refactor, so we approximate time slices
    t_start = time.time()
    results = analyze_noise_pattern(video_path, tmp_out,
                                    sample_frames=sample_frames, noise_sigma=noise_sigma)
    t_analyze = time.time() - t_start
    # After heavy analysis, move progress forward generously
    update_progress(task_id, 80, 'Analyzing noise residuals completed')

    artifacts = {
        'temporal_plot': os.path.join(tmp_out, 'noise_temporal_plot.png'),
        'distribution_plot': os.path.join(tmp_out, 'noise_distribution_plot.png'),
        'visualization': os.path.join(tmp_out, 'noise_visualization.png')
    }
    uploaded = _upload_artifacts(task_id, artifacts)
    
    update_progress(task_id, 97, 'Finalizing results')
    return {
        'taskId': task_id,
        'fileMd5': file_md5,
        'type': 'VIDEO_TRADITIONAL_NOISE',
        'method': 'NOISE',
        'artifacts': uploaded or artifacts,
        'result': results
    }


def process_video_optical_flow(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    file_md5 = task.get('fileMd5') or task_id
    update_progress(task_id, 5, 'Task received')
    video_path = _ensure_video_path(task, task_id)
    out_dir = os.path.join('tmp_py', task_id, 'flow')
    os.makedirs(out_dir, exist_ok=True)
    update_progress(task_id, 20, 'Decoding & sampling frames')
    results = analyze_optical_flow(video_path, out_dir, sample_frames=int(task.get('sampleFrames', 50)))
    update_progress(task_id, 80, 'Optical flow analysis completed')
    artifacts = {
        'flow_visualization': os.path.join(out_dir, 'flow_visualization.png'),
        'flow_magnitude_plot': os.path.join(out_dir, 'flow_magnitude_plot.png'),
        'flow_anomaly_heatmap': os.path.join(out_dir, 'flow_anomaly_heatmap.png')
    }
    uploaded = _upload_artifacts(task_id, artifacts)
    update_progress(task_id, 97, 'Finalizing results')
    return {
        'taskId': task_id,
        'fileMd5': file_md5,
        'type': 'VIDEO_TRADITIONAL_FLOW',
        'method': 'FLOW',
        'artifacts': uploaded or artifacts,
        'result': results
    }


def process_video_frequency(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    file_md5 = task.get('fileMd5') or task_id
    update_progress(task_id, 5, 'Task received')
    video_path = _ensure_video_path(task, task_id)
    out_dir = os.path.join('tmp_py', task_id, 'freq')
    os.makedirs(out_dir, exist_ok=True)
    update_progress(task_id, 20, 'Decoding & sampling frames')
    results = analyze_frequency_domain(video_path, out_dir, sample_frames=int(task.get('sampleFrames', 40)))
    update_progress(task_id, 80, 'Frequency domain analysis completed')
    artifacts = {
        'frequency_spectrum': os.path.join(out_dir, 'frequency_spectrum.png'),
        'frequency_analysis_plot': os.path.join(out_dir, 'frequency_analysis_plot.png'),
        'frequency_heatmap': os.path.join(out_dir, 'frequency_heatmap.png')
    }
    uploaded = _upload_artifacts(task_id, artifacts)
    update_progress(task_id, 97, 'Finalizing results')
    return {
        'taskId': task_id,
        'fileMd5': file_md5,
        'type': 'VIDEO_TRADITIONAL_FREQ',
        'method': 'FREQUENCY',
        'artifacts': uploaded or artifacts,
        'result': results
    }


def process_video_temporal(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    file_md5 = task.get('fileMd5') or task_id
    update_progress(task_id, 5, 'Task received')
    video_path = _ensure_video_path(task, task_id)
    out_dir = os.path.join('tmp_py', task_id, 'temporal')
    os.makedirs(out_dir, exist_ok=True)
    update_progress(task_id, 20, 'Decoding & sampling frames')
    results = detect_temporal_inconsistency(video_path, out_dir)
    update_progress(task_id, 80, 'Temporal inconsistency analysis completed')
    artifacts = {
        'temporal_plot': os.path.join(out_dir, 'temporal_plot.png'),
        'temporal_heatmap': os.path.join(out_dir, 'temporal_heatmap.png')
    }
    uploaded = _upload_artifacts(task_id, artifacts)
    update_progress(task_id, 97, 'Finalizing results')
    return {
        'taskId': task_id,
        'fileMd5': file_md5,
        'type': 'VIDEO_TRADITIONAL_TEMPORAL',
        'method': 'TEMPORAL',
        'artifacts': uploaded or artifacts,
        'result': results
    }


def process_video_copymove(task: dict):
    task_id = task.get('taskId') or task.get('fileMd5') or str(int(time.time()))
    file_md5 = task.get('fileMd5') or task_id
    update_progress(task_id, 5, 'Task received')
    video_path = _ensure_video_path(task, task_id)
    out_dir = os.path.join('tmp_py', task_id, 'copymove')
    os.makedirs(out_dir, exist_ok=True)
    update_progress(task_id, 20, 'Extracting keyframes')
    results = detect_copy_move(video_path, out_dir)
    update_progress(task_id, 80, 'Copy-move detection completed')
    artifacts = {
        'copy_move_heatmap': os.path.join(out_dir, 'copy_move_heatmap.png')
    }
    uploaded = _upload_artifacts(task_id, artifacts)
    update_progress(task_id, 97, 'Finalizing results')
    return {
        'taskId': task_id,
        'fileMd5': file_md5,
        'type': 'VIDEO_TRADITIONAL_COPYMOVE',
        'method': 'COPYMOVE',
        'artifacts': uploaded or artifacts,
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
        auto_offset_reset=KAFKA_OFFSET_RESET
    )
    producer = KafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP,
                             value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                             key_serializer=lambda v: v.encode('utf-8') if v else None)

    for msg in consumer:
        task = msg.value
        task_type = task.get('type')
        task_id = task.get('taskId') or task.get('fileMd5')
        try:
            # Deduplicate processing for the same taskId within a TTL window
            if task_id:
                dedupe_key = f"analysis:processed:{task_id}"
                if rds.exists(dedupe_key):
                    # Skip duplicate message
                    continue
            if task_type == 'IMAGE_AI':
                result = process_image_ai(task)
            elif task_type == 'VIDEO_TRADITIONAL_NOISE':
                result = process_video_traditional(task)
            elif task_type == 'VIDEO_TRADITIONAL_FLOW':
                result = process_video_optical_flow(task)
            elif task_type == 'VIDEO_TRADITIONAL_FREQ':
                result = process_video_frequency(task)
            elif task_type == 'VIDEO_TRADITIONAL_TEMPORAL':
                result = process_video_temporal(task)
            elif task_type == 'VIDEO_TRADITIONAL_COPYMOVE':
                result = process_video_copymove(task)
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
            if task_id:
                try:
                    # Mark as processed for 24h
                    rds.setex(f"analysis:processed:{task_id}", 24*3600, '1')
                except Exception:
                    pass
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
