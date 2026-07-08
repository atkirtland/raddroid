"""Stub of the Unix 'grp' module, which Android's Python build doesn't provide.

Radicale only touches this for a startup diagnostic log line (listing the OS user/
group Radicale runs as), always inside a try/except, so raising is a safe no-op.
"""


def getgrgid(gid):
    raise KeyError(gid)


def getgrnam(name):
    raise KeyError(name)


def getgrall():
    return []
