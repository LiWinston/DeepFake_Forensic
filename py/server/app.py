import os
import sys
import json
from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
from datetime import datetime

# Adjust sys.path to import sibling modules when running as a standalone app
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if os.path.join(BASE_DIR, '2dCNN') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, '2dCNN'))
if os.path.join(BASE_DIR, 'VidTraditional') not in sys.path:
    sys.path.append(os.path.join(BASE_DIR, 'VidTraditional'))

# Reuse existing 2dCNN API endpoints by delegating to its Flask app
from api import app as cnn_app, load_models  # type: ignore

# Video traditional methods (to be wrapped via functions)
from video_noise_pattern import analyze_noise_pattern  # type: ignore
# Future: optical_flow_analysis, temporal_inconsistency, video_copy_move, video_frequency_analysis

app = Flask(__name__)

# Mount CNN blueprint-like under /ai path by forwarding requests
# For simplicity, we'll re-expose select routes and call underlying functions

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'components': {
            'image_cnn': True,
            'video_traditional': True
        },
        'time': datetime.utcnow().isoformat() + 'Z'
    })

@app.route('/ai/models', methods=['GET'])
def ai_models():
    # Delegate to cnn app route handler
    with app.test_request_context():
        return cnn_app.view_functions['list_models']()

@app.route('/ai/predict/image', methods=['POST'])
def ai_predict_image():
    with app.test_request_context():
        return cnn_app.view_functions['predict_image']()

@app.route('/ai/predict/video', methods=['POST'])
def ai_predict_video():
    with app.test_request_context():
        return cnn_app.view_functions['predict_video']()

@app.route('/traditional/video/noise', methods=['POST'])
def traditional_video_noise():
    try:
        # Expect form-data: video (file), sample_frames, noise_sigma
        if 'video' not in request.files:
            return jsonify({'success': False, 'message': 'video file is required'}), 400
        file = request.files['video']
        tmp_dir = os.path.join('tmp', datetime.utcnow().strftime('%Y%m%d%H%M%S'))
        os.makedirs(tmp_dir, exist_ok=True)
        save_path = os.path.join(tmp_dir, secure_filename(file.filename))
        file.save(save_path)

        sample_frames = int(request.form.get('sample_frames', 30))
        noise_sigma = float(request.form.get('noise_sigma', 10.0))

        out_dir = os.path.join(tmp_dir, 'results')
        os.makedirs(out_dir, exist_ok=True)

        results = analyze_noise_pattern(save_path, out_dir, sample_frames=sample_frames, noise_sigma=noise_sigma)

        return jsonify({'success': True, 'data': results, 'artifacts': {
            'temporal_plot': os.path.join(out_dir, 'noise_temporal_plot.png'),
            'distribution_plot': os.path.join(out_dir, 'noise_distribution_plot.png'),
            'visualization': os.path.join(out_dir, 'noise_visualization.png')
        }})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

if __name__ == '__main__':
    # Ensure models are loaded for AI endpoints
    try:
        load_models()
    except Exception as e:
        print('Warning loading models:', e)
    app.run(host='0.0.0.0', port=7000)
