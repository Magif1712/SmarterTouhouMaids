import ctypes
import win32gui
import win32ui
import win32con
import cv2
import time
import numpy as np


def get_window_handle_01(window_title):
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


def capturebit_window(hwnd, target_width, target_height):
    if not win32gui.IsWindowVisible(hwnd) or win32gui.IsIconic(hwnd):
        return None

    # 获取窗口客户区尺寸（不含边框，坐标从 (0,0) 开始）
    client_left, client_top, client_right, client_bottom = win32gui.GetClientRect(hwnd)
    width = client_right - client_left  # 客户区宽度
    height = client_bottom - client_top  # 客户区高度

    hwndDC = win32gui.GetWindowDC(hwnd)
    mfcDC = win32ui.CreateDCFromHandle(hwndDC)
    saveDC = mfcDC.CreateCompatibleDC()

    saveBitMap = win32ui.CreateBitmap()
    saveBitMap.CreateCompatibleBitmap(mfcDC, width, height)
    saveDC.SelectObject(saveBitMap)

    try:
        # 关键修改：源区域从 (0,0) 开始，目标区域也从 (0,0) 开始，尺寸严格匹配客户区
        """
        旧代码
        img = img.reshape((height, width, 4))
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
        resized_img = cv2.resize(img, (target_width, target_height))
        内存占用实在太逆天了，受不了一点
        img = np.frombuffer(signedIntsArray, dtype='uint8')
        img = img.reshape((height, width, 4))
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
        img = cv2.resize(img, (target_width, target_height))
        img = np.unpackbits(img)
        img = img.reshape((target_height, target_width * 24))
        """
        saveDC.BitBlt((0, 0), (width, height), mfcDC, (13, 58), win32con.SRCCOPY)
        signedIntsArray = saveBitMap.GetBitmapBits(True)
        img = np.frombuffer(signedIntsArray, dtype='uint8')
        img = np.unpackbits(img)
        img = img.reshape((height, width * 8, 4))
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
        img = cv2.resize(img, (target_width, target_height))
        img = img.reshape((target_height, -1))
        return img, target_height, target_width
    except Exception as e:
        print(f"Capture error: {e}")
        return None, None, None
    finally:
        win32gui.DeleteObject(saveBitMap.GetHandle())
        saveDC.DeleteDC()
        mfcDC.DeleteDC()
        win32gui.ReleaseDC(hwnd, hwndDC)

# def process_image(byte_data):
#     # 这里可以添加你的图像处理逻辑
#     return byte_data
#
#
# def byte_data_to_image(byte_data, height, width):
#     img = np.frombuffer(byte_data, dtype='uint8')
#     img.shape = (height, width, 3)
#     return img
#
#
# # 使用实例
# window_title = "Minecraft"
# hwnd = get_window_handle_01(window_title)
#
# if hwnd:
#     while True:
#         try:
#             byte_data, height, width = capture_window(hwnd, 1824, 1026)
#             if byte_data is not None:
#                 processed_byte_data = process_image(byte_data)
#                 processed_img = byte_data_to_image(processed_byte_data, height, width)
#
#                 # 验证图像，以幻灯片形式播放
#                 cv2.imshow('Processed Image', processed_img)
#                 cv2.waitKey(100)  # 每 100 毫秒切换一次图像
#             else:
#                 print("Window is not visible or minimized.")
#                 time.sleep(1)
#
#         except Exception as e:
#             print(f"Error: {e}")
#             time.sleep(1)
# else:
#     print(f"未找到包含 '{window_title}' 的窗口。")
#
# cv2.destroyAllWindows()
