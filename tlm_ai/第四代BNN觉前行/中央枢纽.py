from 类BNN import *
from 类FPB import *
from 后台控制器键盘效应器 import *
from 后台控制器鼠标效应器 import *
from 后台窗口视觉信息感受器 import *
import time


def get_window_handle(window_title):
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


def func_receptor_once():
    target_width, target_height = 80, 45
    hwnd = get_window_handle("Minecraft")
    img, height, width = capturebit_window(hwnd, target_width, target_height)
    return img


# while True:
#     a = func_receptor_once() * 200
#     # 按50%比例缩放显示
#     display_img = cv2.resize(a, (a.shape[1] // 1, a.shape[0] // 1))
#     cv2.imshow('Processed Image', display_img)
#     cv2.waitKey(2)
#     # print(a.shape)


def func_effector_once(X):
    # X的[0, 0]和[1, 0]不能用
    global handle
    if X[7] > 0.5:
        key_down(handle, "w")
    else:
        key_up(handle, "w")
    if X[8] > 0.5:
        key_down(handle, "a")
    else:
        key_up(handle, "a")
    if X[9] > 0.5:
        key_down(handle, "s")
    else:
        key_up(handle, "s")
    if X[10] > 0.5:
        key_down(handle, "d")
    else:
        key_up(handle, "d")
    if X[11] > 0.5:
        key_down(handle, "space")
    else:
        key_up(handle, "space")
    if X[12] > 0.75:
        key_down(handle, "f")
    else:
        key_up(handle, "f")
    if X[27] > 0.3:
        try:
            move_to(handle, X[13] * 100 - 50, 0)
            move_to(handle, 0, X[14] * 100 - 50)
        except:
            ...
    if X[15] > 0.5:
        right_down_current(handle)
    else:
        right_up_current(handle)
    if X[16] > 0.5:
        left_down_current(handle)
    else:
        left_up_current(handle)
    if X[17] > 1:
        key_down(handle, "e")
    else:
        key_up(handle, "e")
    if X[18] > 0.5:
        key_down(handle, "1")
    else:
        key_up(handle, "1")
    if X[19] > 0.5:
        key_down(handle, "2")
    else:
        key_up(handle, "2")
    if X[20] > 0.5:
        key_down(handle, "3")
    else:
        key_up(handle, "3")
    if X[21] > 0.5:
        key_down(handle, "4")
    else:
        key_up(handle, "4")
    if X[22] > 0.5:
        key_down(handle, "5")
    else:
        key_up(handle, "5")
    if X[23] > 0.5:
        key_down(handle, "6")
    else:
        key_up(handle, "6")
    if X[24] > 0.5:
        key_down(handle, "7")
    else:
        key_up(handle, "7")
    if X[25] > 0.5:
        key_down(handle, "8")
    else:
        key_up(handle, "8")
    if X[26] > 0.5:
        key_down(handle, "9")
    else:
        key_up(handle, "9")
    pass


handle = get_window_handle("Minecraft")
J_shape = func_receptor_once().shape
X_shape = J_shape
C_shape = (J_shape[0] + X_shape[0], J_shape[1])
fpb = FPB(C_shape, J_shape, X_shape, func_receptor_once, func_effector_once)
