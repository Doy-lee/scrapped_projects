#include "Win32Dsync.h"

#include "Dsync.h"
#include "DsyncConfig.h"
#include "DsyncConsole.h"
#include "dqn.h"

#include <Pathcch.h>  // PathCchRemoveFileSpec
#include <Shellapi.h> // Shell_NotifyIconW
#include <stdio.h>
#include <strsafe.h>

enum Win32Menu
{
	win32menu_exit = 82,
};

typedef struct Win32State
{
	HMENU popUpMenu;
} Win32State;

FILE_SCOPE Win32State globalWin32State;
FILE_SCOPE bool       globalRunning;

#define WIN32_TASKBAR_ICON_UID 0x282ACD13
#define WIN32_TASKBAR_ICON_MSG 0x83AD
FILE_SCOPE LRESULT CALLBACK win32_main_callback(HWND window, UINT msg,
                                                WPARAM wParam, LPARAM lParam)
{
	LRESULT result = 0;
	switch (msg)
	{
		case WM_CREATE:
		{
			globalWin32State.popUpMenu = CreatePopupMenu();
			AppendMenu(globalWin32State.popUpMenu, MF_STRING, win32menu_exit,
			           "Exit");

			// Create Taskbar Icon
			NOTIFYICONDATAW notifyIconData = {};
			notifyIconData.cbSize           = sizeof(notifyIconData);
			notifyIconData.hWnd             = window;
			notifyIconData.uCallbackMessage = WIN32_TASKBAR_ICON_MSG;
			notifyIconData.uID              = WIN32_TASKBAR_ICON_UID;
			notifyIconData.uFlags           = NIF_ICON | NIF_TIP | NIF_MESSAGE;
			notifyIconData.hIcon            = LoadIcon(NULL, IDI_APPLICATION);
			swprintf_s(notifyIconData.szTip,
			           DQN_ARRAY_COUNT(notifyIconData.szTip), L"Dsync");
			DQN_ASSERT(Shell_NotifyIconW(NIM_ADD, &notifyIconData));
		}
		break;

		case WM_COMMAND:
		{
			switch (LOWORD(wParam))
			{
				case win32menu_exit:
				{
					PostQuitMessage(0);
					globalRunning = false;
				}
				break;

				default:
				{
					result = DefWindowProcW(window, msg, wParam, lParam);
				}
				break;
			}
		}
		break;

		case WIN32_TASKBAR_ICON_MSG:
		{
			if (lParam == WM_RBUTTONDOWN)
			{
				POINT p;
				GetCursorPos(&p);

				// A little Windows quirk. You need to do this so the menu
				// disappears if the user clicks off it
				SetForegroundWindow(window);
				u32 clickedCmd = TrackPopupMenu(
				    globalWin32State.popUpMenu,
				    (TPM_LEFTALIGN | TPM_BOTTOMALIGN), p.x, p.y, 0, window, 0);

				// NOTE: win32 documented bug needs this.
				PostMessage(window, WM_NULL, 0, 0);
			}
			else
			{
				result = DefWindowProcW(window, msg, wParam, lParam);
			}
		}
		break;

		default:
		{
			result = DefWindowProcW(window, msg, wParam, lParam);
		}
		break;
	}

	return result;
}

// Return the copied len
u32 win32_get_module_directory(wchar_t *const buf, const u32 bufLen)
{
	if (!buf || bufLen == 0) return 0;
	u32 copiedLen = GetModuleFileNameW(NULL, buf, bufLen);
	if (copiedLen == bufLen)
	{
		DQN_WIN32_ERROR_BOX(
		    "GetModuleFileNameW() buffer maxed: Len of copied text is len "
		    "of supplied buffer.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	// NOTE: Should always work if GetModuleFileNameW works and we're running an
	// executable.
	if (PathCchRemoveFileSpec(buf, copiedLen) == S_FALSE)
	{
		dqn_win32_display_last_error("PathCchRemoveFileSpec() failed");
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return copiedLen;
}

u32 win32_create_unique_timestamp(char *const buf, const i32 bufLen)
{
	if (!buf || bufLen == 0) return 0;

	SYSTEMTIME sysTime = {};
	GetLocalTime(&sysTime);

	u32 len = dqn_sprintf(buf, "%04d-%02d-%02d_%02d%02d%02d", sysTime.wYear,
	                      sysTime.wMonth, sysTime.wDay, sysTime.wHour,
	                      sysTime.wMinute, sysTime.wSecond);

	if (bufLen == len)
	{
		DQN_WIN32_ERROR_BOX(
		    "dqn_sprintf() buffer maxed: Len of copied text is len "
		    "of supplied buffer.",
		    NULL);
		DQN_ASSERT(DQN_INVALID_CODE_PATH);
	}

	return len;
}

u32 win32_make_path_to_directory(const wchar_t *const path,
                                 wchar_t *const buf, const u32 bufLen)
{
	if (!buf || !path) return 0;

	u32 copiedLen = 0;
	i32 pathLen     = dqn_wstrlen(path);
	if (path[pathLen - 1] != '\\')
		copiedLen = swprintf_s(buf, bufLen, L"%s\\", path);
	else
		copiedLen = swprintf_s(buf, bufLen, L"%s", path);

	return copiedLen;
}

VOID CALLBACK win32_monitor_files_callback(PVOID lpParameter,
                                           BOOLEAN TimerOrWaitFired)
{
	DsyncWatchPath *watch = (DsyncWatchPath *)lpParameter;
	OutputDebugString("Monitor file callback\n");

	if (FindNextChangeNotification(watch->monitorHandle) == 0)
	{
		dqn_win32_display_last_error("FindNextChangeNotification() failed");
	}
}

// NOTE: If len is -1, then strlen will be used to determine
bool win32_console_writew(const wchar_t *const string, DWORD len,
                          DWORD *const numWritten)
{
	if (len == -1) len = dqn_wstrlen(string);

	LOCAL_PERSIST HANDLE handle = NULL;
	if (!handle)
	{
		handle = GetStdHandle(STD_OUTPUT_HANDLE);
		if (handle == INVALID_HANDLE_VALUE)
		{
			dqn_win32_display_last_error("GetStdHandle() failed");
			return false;
		}
	}

	if (WriteConsoleW(handle, string, len, numWritten, NULL) == 0)
	{
		dqn_win32_display_last_error("WriteConsoleW() failed");
		return false;
	}

	return true;
}

FILE_SCOPE i32 str_trim_around(char *src, i32 srcLen, const char *charsToTrim,
                               const i32 charsToTrimSize)
{

	// TODO: Implement early exit? Check if first/last char has any of the chars
	// we want to trim, if not, then early exit?
	DQN_ASSERT(srcLen > 0 && charsToTrimSize > 0);

	// srcLen-1 as arrays start from 0, if we want to look at last char
	i32 index = srcLen-1;
	bool matched = false;
	i32 newLen = 0;

	// Starting from EOL if any chars match for trimming, remove and update
	// string length
	for (i32 i = index; i > 0; i--)
	{
		for (i32 j = 0; j < charsToTrimSize; j++)
		{
			if (src[i] == charsToTrim[j])
			{
				src[i]  = 0;
				matched = true;
				break;
			}
		}
		newLen = i + 1;
		if (!matched)
			break;
		else
			matched = false;
	}

	matched = false;
	// Count the number of leading characters to remove
	i32 numLeading = 0;
	for (i32 i = 0; i < newLen; i++)
	{
		for (i32 j = 0; j < charsToTrimSize; j++)
		{
			if (src[i] == charsToTrim[j])
			{
				numLeading++;
				matched = true;
				break;
			}
		}
		if (!matched)
			break;
		else
			matched = false;
	}

	if (numLeading > 0)
	{
		// Shift all chars back how many trash leading elements there are
		for (i32 i = 0; i < newLen; i++)
		{
			src[i] = src[i + numLeading];
		}
		newLen -= numLeading;
	}
	return newLen;
}

FILE_SCOPE void unit_test_internal()
{
	//// DEBUG TEST ////
	const char trim[] = {'"', ' '};
	char brokenString_1[] = "       F:\\test        ";
	char brokenString_2[] = " C:\\   \"   ";

	str_trim_around(brokenString_1, 22, trim, 2);
	DQN_ASSERT(dqn_strcmp(brokenString_1, "F:\\test") == 0);

	str_trim_around(brokenString_2, 11, trim, 2);
	DQN_ASSERT(dqn_strcmp(brokenString_2, "C:\\") == 0);
}

// BIG TODO(doyle): This program only works if, after it detects a change that
// the user continues working and once it hits the threshold, then if it detects
// another change will it backup.

// Reason being is I had trouble getting multithreading to work so I'm taking
// the easy road for now.
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                    LPWSTR lpCmdLine, int nShowCmd)
{
	unit_test_internal();

	////////////////////////////////////////////////////////////////////////////
	// Check Command Line
	////////////////////////////////////////////////////////////////////////////
	// NOTE: In win32 if there are no command line args, argc returns 1. If
	// there is exactly 1 argument, argc also returns 1. Win32 treats the empty
	// command line, the first argument the executable. But if there's an
	// argument then the executable no longer counts in argc.
	// So check the cmd line to see if there are any args in the fist place.
	if (dqn_wstrlen(lpCmdLine) != 0)
	{
		// If arguments supplied, treat the invocation of the program as a
		// command line program

		// NOTE: This is for debugging in Visual Studios. Since there is no console
		// when debugging. But in release, when using the executable on the command
		// line it does have one so we don't need to allocate etc.
#if 0
		AllocConsole();
#else
		if (AttachConsole(ATTACH_PARENT_PROCESS) == 0)
		{
			dqn_win32_display_last_error("AttachConsole() failed");
			return -1;
		}
#endif

		i32 argc;
		wchar_t **argv = CommandLineToArgvW(lpCmdLine, &argc);
		dsync_console_handle_args(argc, argv);
		return 0;
	}

	////////////////////////////////////////////////////////////////////////////
	// Initialise Win32 App
	////////////////////////////////////////////////////////////////////////////
	HWND mainWindow = NULL;
	{
		WNDCLASSEXW wc = {
		    sizeof(WNDCLASSEX),
		    0,
		    win32_main_callback,
		    0, // int cbClsExtra
		    0, // int cbWndExtra
		    hInstance,
		    LoadIcon(NULL, IDI_APPLICATION),
		    LoadCursor(NULL, IDC_ARROW),
		    NULL,
		    L"", // LPCTSTR lpszMenuName
		    L"DsyncWindowClass",
		    NULL, // HICON hIconSm
		};

		if (!RegisterClassExW(&wc))
		{
			DQN_WIN32_ERROR_BOX("RegisterClassExW() failed.", NULL);
			return -1;
		}

		mainWindow =
		    CreateWindowExW(0, wc.lpszClassName, NULL, 0, CW_USEDEFAULT,
		                    CW_USEDEFAULT, 0, 0, NULL, NULL, hInstance, NULL);

		if (!mainWindow)
		{
			DQN_WIN32_ERROR_BOX("CreateWindowExW() failed.", NULL);
			return -1;
		}
	}
	DQN_ASSERT(mainWindow);

	////////////////////////////////////////////////////////////////////////////
	// Read User Data
	////////////////////////////////////////////////////////////////////////////
	DqnPushBuffer pushBuffer = {};
	dqn_push_buffer_init(&pushBuffer, DQN_KILOBYTE(512), 4);
	DsyncLocations locations = dsync_config_load(&pushBuffer);

	if (locations.numBackup <= 0 ||
	    locations.numWatch <= 0)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no backup locations "
		    "and/or watch locations.",
		    NULL);
		return 0;
	}
	else if (!locations.watch || !locations.backup)
	{
		DQN_WIN32_ERROR_BOX(
		    "dsync_config_load() returned empty: There are no strings defined "
		    "in the backup and/or watch locations",
		    NULL);
		return 0;
	}

	////////////////////////////////////////////////////////////////////////////
	// Setup win32 handles for monitoring changes
	////////////////////////////////////////////////////////////////////////////
	HANDLE *fileFindChangeArray = (HANDLE *)dqn_push_buffer_allocate(
	    &pushBuffer, locations.numWatch);
	for (u32 i = 0; i < locations.numWatch; i++)
	{
		DsyncWatchPath *watch = &locations.watch[i];
		const u32 FLAGS  = FILE_NOTIFY_CHANGE_DIR_NAME |
		                  FILE_NOTIFY_CHANGE_LAST_WRITE |
		                  FILE_NOTIFY_CHANGE_FILE_NAME;
		const bool WATCH_ALL_SUBDIRECTORIES = true;

#if 1
		fileFindChangeArray[i] = FindFirstChangeNotificationW(
		    watch->path, WATCH_ALL_SUBDIRECTORIES, FLAGS);

		if (fileFindChangeArray[i] == INVALID_HANDLE_VALUE)
		{
			DQN_WIN32_ERROR_BOX("FindFirstChangeNotification() failed.", NULL);
			return -1;
		}
#else
		watch->monitorHandle = FindFirstChangeNotificationW(
		    watch->path, WATCH_ALL_SUBDIRECTORIES, FLAGS);
		if (watch->monitorHandle == INVALID_HANDLE_VALUE)
		{
			DQN_WIN32_ERROR_BOX("FindFirstChangeNotification() failed.", NULL);
			return -1;
		}

		RegisterWaitForSingleObject(&watch->waitHandle, watch->monitorHandle,
		                            win32_monitor_files_callback, (PVOID)watch,
		                            INFINITE,
		                            WT_EXECUTEDEFAULT | WT_EXECUTEONLYONCE);

#endif
	}

	globalRunning = true;
	while (globalRunning)
	{
		MSG msg;
#if 1
		while (PeekMessage(&msg, mainWindow, 0, 0, PM_REMOVE))
		{
			TranslateMessage(&msg);
			DispatchMessageW(&msg);
		}

		////////////////////////////////////////////////////////////////////////
		// Begin blocking call to watch files
		////////////////////////////////////////////////////////////////////////
		const i32 NUM_HANDLES = locations.numWatch;
		DWORD waitSignalled   = WaitForMultipleObjects(
		    NUM_HANDLES, fileFindChangeArray, false, 500);

		////////////////////////////////////////////////////////////////////////
		// File Changes Detected
		////////////////////////////////////////////////////////////////////////
		if (waitSignalled == WAIT_TIMEOUT)
		{
			for (u32 i = 0; i < locations.numWatch; i++)
			{
				DsyncWatchPath *watch = &locations.watch[i];
				if (watch->numChanges > 0)
				{
					const f32 MIN_TIME_BETWEEN_BACKUP_IN_S = (60.0f * 2.0f);

					f32 elapsedSec =
					    (f32)dqn_time_now_in_s() - watch->changeTimestamp;
					DQN_ASSERT(watch->changeTimestamp > 0);
					DQN_ASSERT(elapsedSec >= 0);

					if (elapsedSec >= MIN_TIME_BETWEEN_BACKUP_IN_S)
					{
						dsync_backup(watch->path, locations.backup,
						             locations.numBackup);

						NOTIFYICONDATAW notifyIconData = {};
						notifyIconData.cbSize          = sizeof(notifyIconData);
						notifyIconData.hWnd            = mainWindow;
						notifyIconData.uID             = WIN32_TASKBAR_ICON_UID;
						notifyIconData.uFlags = NIF_INFO | NIF_REALTIME;
						notifyIconData.hIcon  = LoadIcon(NULL, IDI_APPLICATION);
						notifyIconData.dwState = NIS_SHAREDICON;
						swprintf_s(notifyIconData.szInfo,
						           DQN_ARRAY_COUNT(notifyIconData.szInfo),
						           L"Backing up %d changes in \"%s\"",
						           watch->numChanges, watch->path);

						swprintf_s(notifyIconData.szInfoTitle,
						           DQN_ARRAY_COUNT(notifyIconData.szInfoTitle),
						           L"Dsync");

						notifyIconData.dwInfoFlags =
						    NIIF_INFO | NIIF_NOSOUND | NIIF_RESPECT_QUIET_TIME;
						DQN_ASSERT(
						    Shell_NotifyIconW(NIM_MODIFY, &notifyIconData));

						watch->numChanges      = 0;
						watch->changeTimestamp = 0;

					}
				}
			}
		}
		else
		{
			if (waitSignalled == WAIT_FAILED)
			{
				dqn_win32_display_last_error("WaitForMultipleObjects() failed");
				return 0;
			}

			i32 signalIndex = waitSignalled - WAIT_OBJECT_0;
			DQN_ASSERT(signalIndex >= 0 && signalIndex < NUM_HANDLES);
			if (FindNextChangeNotification(fileFindChangeArray[signalIndex]) ==
			    0)
			{
				dqn_win32_display_last_error(
				    "FindNextChangeNotification() failed");
				return 0;
			}

			////////////////////////////////////////////////////////////////////////
			// Mark on the watched path that a change has been registered
			////////////////////////////////////////////////////////////////////////
			DsyncWatchPath *watch = &locations.watch[signalIndex];
			watch->numChanges++;

			// NOTE: If first time detected change, don't backup until we change
			// it again after the minimum time between backup
			if (watch->changeTimestamp == 0)
				watch->changeTimestamp = (f32)dqn_time_now_in_s();

			// TODO(doyle): This triggers multiple times for one change. Why?
			// Finding out why might solve our multithreading problem and let us
			// use that method .. which would be much better.
			OutputDebugString("Detected file change in watch list\n");
		}

#else
		while (GetMessage(&msg, mainWindow, 0, 0) > 0)
		{
			TranslateMessage(&msg);
			DispatchMessageW(&msg);
		}
#endif
	}

	return 0;
}
