import subprocess
import time
from datetime import datetime
import re
import sys

PACKAGE_NAME = "com.legacydroid.luminaai"
OUTPUT_FILE = "fps_log.txt"
INTERVAL = 0.2  # 200 ms

def get_rendered_frames(package):
    """Fetches frames rendered since last reset and resets the counter."""
    try:
        res = subprocess.run(
            ["adb", "shell", "dumpsys", "gfxinfo", package, "reset"],
            capture_output=True,
            text=True,
            timeout=2
        )
        match = re.search(r"Total frames rendered:\s+(\d+)", res.stdout)
        if match:
            return int(match.group(1))
    except Exception:
        pass
    return 0

def main():
    print(f"Tracking FPS for {PACKAGE_NAME} (refresh: 200ms)...")
    print(f"Logging to: {OUTPUT_FILE} (Press Ctrl+C to stop)\n")

    # Initial reset to clear old history
    get_rendered_frames(PACKAGE_NAME)
    last_time = time.perf_counter()

    with open(OUTPUT_FILE, "a") as f:
        while True:
            time.sleep(INTERVAL)
            
            now = time.perf_counter()
            delta_time = now - last_time
            last_time = now

            frames = get_rendered_frames(PACKAGE_NAME)
            fps = round(frames / delta_time, 1) if delta_time > 0 else 0

            # Format: HH-MM-SS:FPS
            timestamp = datetime.now().strftime("%H-%M-%S")
            log_line = f"{timestamp}:{fps}"

            # Print to terminal in real time
            print(log_line)

            # Write to file
            f.write(log_line + "\n")
            f.flush()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nRecording stopped.")
