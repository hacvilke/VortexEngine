#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <shellapi.h>
#include <wchar.h>

static BOOL file_exists(const wchar_t *path) {
    DWORD attr = GetFileAttributesW(path);
    return (attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY));
}

static BOOL search_vendor_dir(const wchar_t *baseDir, wchar_t *outJavaw, size_t maxChars) {
    wchar_t pattern[MAX_PATH];
    wsprintfW(pattern, L"%s\\*", baseDir);

    WIN32_FIND_DATAW fd;
    HANDLE hFind = FindFirstFileW(pattern, &fd);
    if (hFind == INVALID_HANDLE_VALUE) return FALSE;

    BOOL found = FALSE;
    do {
        if ((fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) &&
            wcscmp(fd.cFileName, L".") != 0 && wcscmp(fd.cFileName, L"..") != 0) {
            wchar_t candidate[MAX_PATH];
            wsprintfW(candidate, L"%s\\%s\\bin\\javaw.exe", baseDir, fd.cFileName);
            if (file_exists(candidate)) {
                wcsncpy(outJavaw, candidate, maxChars - 1);
                outJavaw[maxChars - 1] = L'\0';
                found = TRUE;
                // Keep iterating in case a newer version sorts later alphabetically
            }
        }
    } while (FindNextFileW(hFind, &fd));

    FindClose(hFind);
    return found;
}

static void resolve_javaw(const wchar_t *exeDir, wchar_t *outJavaw, size_t maxChars) {
    wchar_t candidate[MAX_PATH];

    // 1. Portable bundled ./jre/bin/javaw.exe alongside the .exe
    wsprintfW(candidate, L"%s\\jre\\bin\\javaw.exe", exeDir);
    if (file_exists(candidate)) {
        wcsncpy(outJavaw, candidate, maxChars - 1);
        return;
    }

    // 2. JAVA_HOME environment variable
    wchar_t javaHome[MAX_PATH];
    DWORD len = GetEnvironmentVariableW(L"JAVA_HOME", javaHome, MAX_PATH);
    if (len > 0 && len < MAX_PATH) {
        wsprintfW(candidate, L"%s\\bin\\javaw.exe", javaHome);
        if (file_exists(candidate)) {
            wcsncpy(outJavaw, candidate, maxChars - 1);
            return;
        }
    }

    // 3. Standard Windows JDK/JRE vendor directories
    const wchar_t *vendors[] = {
        L"C:\\Program Files\\Eclipse Adoptium",
        L"C:\\Program Files\\Java",
        L"C:\\Program Files\\Microsoft",
        L"C:\\Program Files\\BellSoft",
        L"C:\\Program Files\\Zulu",
        L"C:\\Program Files\\Amazon Corretto",
        L"C:\\Program Files (x86)\\Eclipse Adoptium",
        L"C:\\Program Files (x86)\\Java"
    };
    for (int i = 0; i < (int)(sizeof(vendors) / sizeof(vendors[0])); i++) {
        if (search_vendor_dir(vendors[i], outJavaw, maxChars)) {
            return;
        }
    }

    // 4. Fallback to javaw.exe on system PATH
    wcsncpy(outJavaw, L"javaw.exe", maxChars - 1);
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR pCmdLine, int nCmdShow) {
    (void)hInstance; (void)hPrevInstance; (void)nCmdShow;

    wchar_t selfPath[MAX_PATH];
    if (!GetModuleFileNameW(NULL, selfPath, MAX_PATH)) {
        MessageBoxW(NULL, L"Failed to determine executable path.", L"Vortex Engine", MB_ICONERROR | MB_OK);
        return 1;
    }

    wchar_t exeDir[MAX_PATH];
    wcsncpy(exeDir, selfPath, MAX_PATH - 1);
    exeDir[MAX_PATH - 1] = L'\0';
    wchar_t *lastSlash = wcsrchr(exeDir, L'\\');
    if (lastSlash) *lastSlash = L'\0';

    wchar_t javaw[MAX_PATH];
    javaw[0] = L'\0';
    resolve_javaw(exeDir, javaw, MAX_PATH);

    // Prepend resolved JDK/JRE bin directory to PATH so child shells & tools find java/javac immediately
    wchar_t javaBinDir[MAX_PATH];
    wcsncpy(javaBinDir, javaw, MAX_PATH - 1);
    javaBinDir[MAX_PATH - 1] = L'\0';
    wchar_t *binSlash = wcsrchr(javaBinDir, L'\\');
    if (binSlash) {
        *binSlash = L'\0';
        wchar_t oldPath[16384];
        DWORD pLen = GetEnvironmentVariableW(L"PATH", oldPath, 16384);
        wchar_t newPath[18000];
        if (pLen > 0 && pLen < 16384) {
            wsprintfW(newPath, L"%s;%s", javaBinDir, oldPath);
        } else {
            wsprintfW(newPath, L"%s", javaBinDir);
        }
        SetEnvironmentVariableW(L"PATH", newPath);
    }

    wchar_t cmdLine[4096];
    if (pCmdLine && pCmdLine[0] != L'\0') {
        wsprintfW(cmdLine, L"\"%s\" -Xmx1024m -Dfile.encoding=UTF-8 -Dsun.java2d.d3d=true -jar \"%s\" %s", javaw, selfPath, pCmdLine);
    } else {
        wsprintfW(cmdLine, L"\"%s\" -Xmx1024m -Dfile.encoding=UTF-8 -Dsun.java2d.d3d=true -jar \"%s\"", javaw, selfPath);
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessW(NULL, cmdLine, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, exeDir, &si, &pi)) {
        int resp = MessageBoxW(
            NULL,
            L"Vortex Engine Studio requires Java 11+ (64-bit) to run on Windows.\n\n"
            L"Tip: You can either install Eclipse Temurin JRE/JDK OR place a portable 'jre' folder next to this .exe.\n\n"
            L"Would you like to open https://adoptium.net to download Java now?",
            L"Vortex Engine Studio v4.2.0 — Java Runtime Needed",
            MB_ICONINFORMATION | MB_YESNO
        );
        if (resp == IDYES) {
            ShellExecuteW(NULL, L"open", L"https://adoptium.net/temurin/releases/", NULL, NULL, SW_SHOWNORMAL);
        }
        return 2;
    }

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 0;
}
