from flask import Flask, request, jsonify
import json
from datetime import datetime
import os

app = Flask(__name__)

# File untuk menyimpan data perangkat secara persisten
DATA_FILE = 'devices_data.json'

# --- Fungsi untuk memuat dan menyimpan data perangkat ---
def load_devices_data():
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, 'r') as f:
            return json.load(f)
    return {}

def save_devices_data(data):
    with open(DATA_FILE, 'w') as f:
        json.dump(data, f, indent=4)

devices = load_devices_data()

# --- Endpoint untuk bocah-app ---

@app.route('/register_key', methods=['POST'])
def register_key():
    data = request.json
    device_id = data.get('device_id')
    encryption_key = data.get('encryption_key')

    if not device_id or not encryption_key:
        return jsonify({"message": "Device ID atau kunci enkripsi tidak ada"}), 400

    if device_id not in devices:
        devices[device_id] = {
            "encryption_key": encryption_key,
            "last_seen": datetime.now().isoformat(),
            "status": "registered",
            "pending_command": None,
            "current_location": {"latitude": "N/A", "longitude": "N/A", "timestamp": "N/A"} # Tambahkan fitur lokasi
        }
    else:
        devices[device_id]["encryption_key"] = encryption_key
        devices[device_id]["last_seen"] = datetime.now().isoformat()
        devices[device_id]["status"] = "key_updated"
    
    save_devices_data(devices)
    print(f"[{datetime.now().isoformat()}] Device {device_id} registered/updated key.")
    return jsonify({"message": "Kunci enkripsi terdaftar/diperbarui"}), 200

@app.route('/update_location', methods=['POST'])
def update_location():
    data = request.json
    device_id = data.get('device_id')
    latitude = data.get('latitude')
    longitude = data.get('longitude')

    if not device_id or not latitude or not longitude:
        return jsonify({"message": "Data lokasi tidak lengkap"}), 400
    
    if device_id in devices:
        devices[device_id]["current_location"] = {
            "latitude": latitude,
            "longitude": longitude,
            "timestamp": datetime.now().isoformat()
        }
        devices[device_id]["last_seen"] = datetime.now().isoformat()
        save_devices_data(devices)
        print(f"[{datetime.now().isoformat()}] Device {device_id} location updated: {latitude}, {longitude}")
        return jsonify({"message": "Lokasi diperbarui"}), 200
    else:
        return jsonify({"message": "Device ID tidak ditemukan"}), 404


@app.route('/get_command', methods=['GET'])
def get_command():
    device_id = request.args.get('device_id')

    if not device_id or device_id not in devices:
        return jsonify({"command": "NONE"}), 200

    command_to_send = devices[device_id].get("pending_command")
    
    if command_to_send:
        print(f"[{datetime.now().isoformat()}] Sending command to {device_id}: {command_to_send.get('command')}")
        return jsonify(command_to_send), 200
    
    return jsonify({"command": "NONE"}), 200

@app.route('/clear_command', methods=['POST'])
def clear_command():
    data = request.json
    device_id = data.get('device_id')

    if not device_id or device_id not in devices:
        return jsonify({"message": "Device ID tidak ditemukan"}), 404
    
    devices[device_id]["pending_command"] = None
    save_devices_data(devices)
    print(f"[{datetime.now().isoformat()}] Command cleared for {device_id}.")
    return jsonify({"message": "Perintah dihapus"}), 200

@app.route('/post_status', methods=['POST'])
def post_status():
    data = request.json
    device_id = data.get('device_id')
    status = data.get('status')

    if not device_id or not status:
        return jsonify({"message": "Device ID atau status tidak ada"}), 400

    if device_id in devices:
        devices[device_id]["status"] = status
        devices[device_id]["last_seen"] = datetime.now().isoformat()
        save_devices_data(devices)
        print(f"[{datetime.now().isoformat()}] Device {device_id} status updated to: {status}")
        return jsonify({"message": "Status diperbarui"}), 200
    else:
        return jsonify({"message": "Device ID tidak ditemukan"}), 404

# --- Endpoint untuk ortu-app ---

@app.route('/send_command', methods=['POST'])
def send_command():
    data = request.json
    device_id = data.get('device_id')
    command = data.get('command')
    key_for_decryption = data.get('key_for_decryption')

    if not device_id or not command:
        return jsonify({"message": "Device ID atau perintah tidak ada"}), 400

    if device_id in devices:
        command_payload = {"command": command}
        if key_for_decryption:
            command_payload["key_for_decryption"] = key_for_decryption
        
        devices[device_id]["pending_command"] = command_payload
        save_devices_data(devices)
        print(f"[{datetime.now().isoformat()}] Command '{command}' set for {device_id}.")
        return jsonify({"message": "Perintah diatur, menunggu eksekusi perangkat"}), 200
    else:
        return jsonify({"message": "Device ID tidak ditemukan"}), 404

@app.route('/get_key', methods=['GET'])
def get_key():
    device_id = request.args.get('device_id')

    if not device_id or device_id not in devices:
        return jsonify({"message": "Device ID tidak ditemukan atau kunci tidak ada"}), 404
    
    key = devices[device_id].get("encryption_key", "N/A")
    return jsonify({"encryption_key": key}), 200

@app.route('/get_status', methods=['GET'])
def get_status():
    device_id = request.args.get('device_id')

    if not device_id or device_id not in devices:
        return jsonify({"message": "Device ID tidak ditemukan"}), 404
    
    device_info = devices[device_id]
    return jsonify({
        "status": device_info.get("status", "Unknown"),
        "last_seen": device_info.get("last_seen", "N/A"),
        "current_location": device_info.get("current_location", {"latitude": "N/A", "longitude": "N/A", "timestamp": "N/A"})
    }), 200

# --- Endpoint untuk melihat semua perangkat terdaftar (opsional untuk debugging/admin) ---
@app.route('/devices', methods=['GET'])
def list_devices():
    return jsonify(devices), 200

if __name__ == '__main__':
    # Pastikan file data ada saat pertama kali dijalankan
    if not os.path.exists(DATA_FILE):
        save_devices_data({})
    app.run(host='0.0.0.0', port=5000, debug=True)
