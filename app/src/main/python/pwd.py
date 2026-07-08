"""Stub of the Unix 'pwd' module, which Android's Python build doesn't provide.

Radicale only touches this for a startup diagnostic log line (listing the OS user/
group Radicale runs as), always inside a try/except, so raising is a safe no-op.
"""


def getpwuid(uid):
    raise KeyError(uid)


def getpwnam(name):
    raise KeyError(name)
