from ctypes import windll, byref
from ctypes.wintypes import HWND, POINT
import time
import ctypes
from ctypes import wintypes
from ctypes.wintypes import HWND

PostMessageW = windll.user32.PostMessageW
ClientToScreen = windll.user32.ClientToScreen

WM_MOUSEMOVE = 0x0200
WM_LBUTTONDOWN = 0x0201
WM_LBUTTONUP = 0x202
WM_RBUTTONDOWN = 0x0204
WM_RBUTTONUP = 0x0205
WM_MOUSEWHEEL = 0x020A
WHEEL_DELTA = 120

GetCursorPos = windll.user32.GetCursorPos


def get_window_handle_03(window_title):
    EnumWindows = ctypes.windll.user32.EnumWindows
    # 正确修改，避免使用 ctypes.wintypes
    EnumWindowsProc = ctypes.CFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)

    def enum_callback(hwnd, lparam):
        length = ctypes.windll.user32.GetWindowTextLengthW(hwnd) + 1
        title = ctypes.create_unicode_buffer(length)
        ctypes.windll.user32.GetWindowTextW(hwnd, title, length)
        if window_title in title.value:
            nonlocal found_hwnd
            found_hwnd = hwnd
            return False  # 停止遍历
        return True

    found_hwnd = 0
    EnumWindows(EnumWindowsProc(enum_callback), 0)
    return found_hwnd


def move_to(handle: HWND, x: int, y: int):
    """移动鼠标到坐标（x, y)

    Args:
        handle (HWND): 窗口句柄
        x (int): 横坐标
        y (int): 纵坐标
    """
    # https://docs.microsoft.com/en-us/windows/win32/inputdev/wm-mousemove
    wparam = 0
    lparam = y << 16 | x
    PostMessageW(handle, WM_MOUSEMOVE, wparam, lparam)


def left_down(handle: HWND, x: int, y: int):
    """在坐标(x, y)按下鼠标左键

    Args:
        handle (HWND): 窗口句柄
        x (int): 横坐标
        y (int): 纵坐标
    """
    # https://docs.microsoft.com/en-us/windows/win32/inputdev/wm-lbuttondown
    wparam = 0
    lparam = y << 16 | x
    PostMessageW(handle, WM_LBUTTONDOWN, wparam, lparam)


def left_down_current(handle: HWND):
    """在鼠标当前位置按下鼠标左键

    Args:
        handle (HWND): 窗口句柄
    """
    p = POINT()
    # 获取当前鼠标位置
    if GetCursorPos(byref(p)):
        x = p.x
        y = p.y
        wparam = 0
        lparam = y << 16 | x
        # 发送鼠标左键按下消息
        PostMessageW(handle, WM_LBUTTONDOWN, wparam, lparam)


def left_up(handle: HWND, x: int, y: int):
    """在坐标(x, y)放开鼠标左键

    Args:
        handle (HWND): 窗口句柄
        x (int): 横坐标
        y (int): 纵坐标
    """
    # https://docs.microsoft.com/en-us/windows/win32/inputdev/wm-lbuttonup
    wparam = 0
    lparam = y << 16 | x
    PostMessageW(handle, WM_LBUTTONUP, wparam, lparam)


def left_up_current(handle: HWND):
    """在鼠标当前位置放开鼠标左键

    Args:
        handle (HWND): 窗口句柄
    """
    p = POINT()
    # 获取当前鼠标位置
    if GetCursorPos(byref(p)):
        x = p.x
        y = p.y
        wparam = 0
        lparam = y << 16 | x
        # 发送鼠标左键按下消息
        PostMessageW(handle, WM_LBUTTONUP, wparam, lparam)


def right_down_current(handle: HWND):
    """在鼠标当前位置按下鼠标右键

    Args:
        handle (HWND): 窗口句柄
    """
    p = POINT()
    # 获取当前鼠标位置
    if GetCursorPos(byref(p)):
        x = p.x
        y = p.y
        wparam = 0
        lparam = y << 16 | x
        # 发送鼠标右键按下消息
        PostMessageW(handle, WM_RBUTTONDOWN, wparam, lparam)


def right_up_current(handle: HWND):
    """在鼠标当前位置放开鼠标右键

    Args:
        handle (HWND): 窗口句柄
    """
    p = POINT()
    # 获取当前鼠标位置
    if GetCursorPos(byref(p)):
        x = p.x
        y = p.y
        wparam = 0
        lparam = y << 16 | x
        # 发送鼠标右键放开消息
        PostMessageW(handle, WM_RBUTTONUP, wparam, lparam)


def scroll(handle: HWND, delta: int, x: int, y: int):
    """在坐标(x, y)滚动鼠标滚轮

    Args:
        handle (HWND): 窗口句柄
        delta (int): 为正向上滚动，为负向下滚动
        x (int): 横坐标
        y (int): 纵坐标
    """
    move_to(handle, x, y)
    # https://docs.microsoft.com/en-us/windows/win32/inputdev/wm-mousewheel
    wparam = delta << 16
    p = POINT(x, y)
    ClientToScreen(handle, byref(p))
    lparam = p.y << 16 | p.x
    PostMessageW(handle, WM_MOUSEWHEEL, wparam, lparam)


def scroll_current(handle: HWND, delta: int):
    """在鼠标当前位置滚动鼠标滚轮

    Args:
        handle (HWND): 窗口句柄
        delta (int): 为正向上滚动，为负向下滚动
    """
    p = POINT()
    # 获取当前鼠标位置
    if GetCursorPos(byref(p)):
        # https://docs.microsoft.com/en-us/windows/win32/inputdev/wm-mousewheel
        wparam = delta << 16
        ClientToScreen(handle, byref(p))
        lparam = p.y << 16 | p.x
        PostMessageW(handle, WM_MOUSEWHEEL, wparam, lparam)


def scroll_up(handle: HWND, x: int, y: int):
    """在坐标(x, y)向上滚动鼠标滚轮

    Args:
        handle (HWND): 窗口句柄
        x (int): 横坐标
        y (int): 纵坐标
    """
    scroll(handle, WHEEL_DELTA, x, y)


def scroll_down(handle: HWND, x: int, y: int):
    """在坐标(x, y)向下滚动鼠标滚轮

    Args:
        handle (HWND): 窗口句柄
        x (int): 横坐标
        y (int): 纵坐标
    """
    scroll(handle, -WHEEL_DELTA, x, y)


# if __name__ == "__main__":
#     # 需要和目标窗口同一权限，游戏窗口通常是管理员权限
#     import sys
#
#     if not windll.shell32.IsUserAnAdmin():
#         # 不是管理员就提权
#         windll.shell32.ShellExecuteW(
#             None, "runas", sys.executable, __file__, None, 1)
#
#     # 获取窗口句柄
#     hwnd = get_window_handle_03("Minecraft")
#     if hwnd == 0:
#         raise RuntimeError("未找到Minecraft窗口")  # 点击线路
#     # left_down(hwnd, 1234, 20)
#     right_down_current(hwnd)
#     time.sleep(1)
#     # left_up(hwnd, 1234, 20)
#     right_up_current(hwnd)
#     time.sleep(1)
#     # 滚动线路列表
#     scroll_down(hwnd, 1000, 200)
