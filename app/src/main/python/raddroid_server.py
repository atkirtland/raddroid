"""Runs Radicale in a background thread, controlled by RadicaleService.kt."""

import os
import socket
import threading

from radicale import config as radicale_config
from radicale import server as radicale_server

_lock = threading.Lock()
_thread = None
_shutdown_write = None
_error = ""


def is_running():
    return _thread is not None and _thread.is_alive()


def last_error():
    return _error


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
