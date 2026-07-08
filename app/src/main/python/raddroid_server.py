"""Runs Radicale in a background thread, controlled by RadicaleService.kt."""

import logging
import os
import socket
import threading
from collections import deque

from radicale import config as radicale_config
from radicale import server as radicale_server

_lock = threading.Lock()
_thread = None
_shutdown_write = None
_error = ""

# Radicale logs everything (startup banner, auth warnings, per-request lines) through
# logging.getLogger("radicale"), which normally only reaches sys.stderr/logcat. Attaching
# our own handler directly to that logger (rather than root, which Radicale's own
# log.setup() reconfigures via logging.basicConfig) captures the same output for display
# inside the app, without touching Radicale's own stderr handler.
_log_lines = deque(maxlen=2000)


class _MemoryLogHandler(logging.Handler):
    def emit(self, record):
        try:
            for line in self.format(record).splitlines():
                _log_lines.append(line)
        except Exception:
            pass


_log_handler = _MemoryLogHandler()
_log_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s", "%H:%M:%S"))
logging.getLogger("radicale").addHandler(_log_handler)


def is_running():
    return _thread is not None and _thread.is_alive()


def last_error():
    return _error


def get_log():
    return "\n".join(_log_lines)


def clear_log():
    _log_lines.clear()


def start(config_path, working_dir):
    global _thread, _shutdown_write, _error
    with _lock:
        if is_running():
            return "already-running"

        _error = ""
        os.makedirs(working_dir, exist_ok=True)
        os.chdir(working_dir)

        configuration = radicale_config.load(
            radicale_config.parse_compound_paths(config_path))

        shutdown_read, shutdown_write = socket.socketpair()
        _shutdown_write = shutdown_write

        def run():
            global _error
            try:
                radicale_server.serve(configuration, shutdown_read)
            except Exception:
                import traceback
                _error = traceback.format_exc()
            finally:
                shutdown_read.close()

        _thread = threading.Thread(target=run, name="radicale-server", daemon=True)
        _thread.start()
        return "started"


def stop():
    global _shutdown_write
    with _lock:
        if _shutdown_write is not None:
            try:
                _shutdown_write.close()
            except OSError:
                pass
            _shutdown_write = None
        if _thread is not None:
            _thread.join(timeout=5)
        return "stopped"
