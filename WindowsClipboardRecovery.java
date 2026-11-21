// 화면 입력과 작업 표시줄 알림 영역에 필요한 Java AWT 클래스
import java.awt.AWTException; // 트레이 아이콘 추가 등 화면 기능이 실패할 때 발생하는 예외
import java.awt.Color; // 트레이 아이콘의 배경색과 글자색
import java.awt.Font; // 트레이 아이콘에 그릴 '한' 글자의 글꼴
import java.awt.Graphics2D; // 트레이 아이콘 이미지에 배경과 글자를 그리는 도구
import java.awt.MenuItem; // 아이콘 오른쪽 클릭 메뉴의 '종료' 항목
import java.awt.PopupMenu; // 트레이 아이콘의 오른쪽 클릭 메뉴
import java.awt.Robot; // 선택한 글자를 복사·붙여넣기 위해 키 입력을 보냄
import java.awt.SystemTray; // Windows 작업 표시줄 알림 영역에 접근
import java.awt.Toolkit; // Windows 시스템 클립보드를 가져옴
import java.awt.TrayIcon; // 알림 영역에 표시할 아이콘과 설명, 메뉴

// 선택한 글자를 읽고 변환 결과를 전달할 클립보드 클래스
import java.awt.datatransfer.Clipboard; // 복사한 글자를 읽고 변환 결과를 넣는 클립보드
import java.awt.datatransfer.DataFlavor; // 클립보드 내용이 문자열인지 판별
import java.awt.datatransfer.StringSelection; // 문자열을 클립보드에 넣을 수 있는 형태로 만듦
import java.awt.datatransfer.Transferable; // 클립보드에 저장할 내용의 공통 자료형
import java.awt.datatransfer.UnsupportedFlavorException; // 클립보드 내용을 문자열로 읽을 수 없을 때의 예외

// 단축키의 키 번호, 트레이 이미지, 입출력 오류
import java.awt.event.KeyEvent; // Ctrl, Q, C, V의 키 번호
import java.awt.image.BufferedImage; // 코드에서 직접 만드는 트레이 아이콘 이미지
import java.io.IOException; // 클립보드 내용을 읽는 중 발생할 수 있는 입출력 예외

// Java에서 Windows DLL의 함수를 호출하기 위한 FFM API
import java.lang.foreign.Arena; // Windows 함수에 전달할 메모리를 만들고 정리
import java.lang.foreign.FunctionDescriptor; // Windows 함수의 인수·반환값 자료형을 설명
import java.lang.foreign.Linker; // Java와 Windows 함수를 연결
import java.lang.foreign.MemoryLayout; // Windows 데이터가 메모리에 놓인 구조를 설명
import java.lang.foreign.MemorySegment; // Windows 함수와 주고받는 메모리 영역
import java.lang.foreign.SymbolLookup; // Windows DLL에서 함수 이름을 찾아냄
import java.lang.foreign.ValueLayout; // int, short, 주소 같은 값의 메모리 형식을 지정
import java.lang.invoke.MethodHandle; // 찾아낸 Windows 함수를 나중에 호출할 수 있게 보관

// 실행 환경 확인과 오류 대화상자
import java.util.Locale; // 운영체제 이름을 대소문자에 관계없이 확인
import javax.swing.JOptionPane; // 창 없는 실행 파일에서 오류를 대화상자로 알림

/**
 * Windows에서 선택한 글자를 Ctrl+Q로 변환해 같은 위치에 붙여넣는 프로그램.
 *
 * 사용 순서: 편집 가능한 글자를 선택한 뒤 Ctrl+Q를 누른다.
 * 처리 흐름: 단축키 등록 → 선택 영역 복사 → KoreanEnglishRecovery로 변환 → 붙여넣기.
 */
public class WindowsClipboardRecovery {
    // RegisterHotKey에 등록할 Ctrl+Q와 메시지 루프에서 사용할 Windows 상수
    private static final int HOTKEY_ID = 1;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_NOREPEAT = 0x4000;
    private static final int VK_Q = KeyEvent.VK_Q;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;
    private static final int ERROR_HOTKEY_ALREADY_REGISTERED = 1409;

    // FFM API로 연결한 Windows 함수들을 보관한다. 실행 대상은 JDK 25와 64비트 Windows다.
    private static MethodHandle registerHotKey;
    private static MethodHandle unregisterHotKey;
    private static MethodHandle getMessage;
    private static MethodHandle getAsyncKeyState;
    private static MethodHandle getForegroundWindow;
    private static MethodHandle getCurrentThreadId;
    private static MethodHandle postThreadMessage;

    /** user32.dll과 kernel32.dll에서 단축키, 키 상태, 창, 메시지 관련 함수를 연결한다. */
    private static void connectWindowsFunctions() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());

        // 전역 단축키 등록과 해제. 등록 직후에는 Windows 오류 코드도 함께 읽는다.
        registerHotKey = linker.downcallHandle(user32.findOrThrow("RegisterHotKey"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
            Linker.Option.captureCallState("GetLastError"));
        unregisterHotKey = linker.downcallHandle(user32.findOrThrow("UnregisterHotKey"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // 단축키와 트레이 종료 요청을 받는 Windows 메시지 루프, 키 눌림 상태 확인
        getMessage = linker.downcallHandle(user32.findOrThrow("GetMessageW"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        getAsyncKeyState = linker.downcallHandle(user32.findOrThrow("GetAsyncKeyState"),
            FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));

        // 처리 시작과 붙여넣기 직전에 같은 편집 창인지 확인하고 트레이 종료 메시지를 보낸다.
        getForegroundWindow = linker.downcallHandle(user32.findOrThrow("GetForegroundWindow"),
            FunctionDescriptor.of(ValueLayout.ADDRESS));
        getCurrentThreadId = linker.downcallHandle(
            SymbolLookup.libraryLookup("kernel32", Arena.global()).findOrThrow("GetCurrentThreadId"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        postThreadMessage = linker.downcallHandle(user32.findOrThrow("PostThreadMessageW"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    }

    /** 실행 환경을 확인하고 Ctrl+Q를 등록한 뒤, 단축키 또는 종료 메시지를 기다린다. */
    public static void main(String[] args) {
        // Windows와 트레이를 사용할 수 없는 환경에서는 실행을 시작하지 않는다.
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            System.err.println("이 시험 프로그램은 Windows에서만 실행할 수 있습니다.");
            return;
        }
        if (ValueLayout.ADDRESS.byteSize() != 8) {
            System.err.println("현재 버전은 64비트 Windows에서만 실행할 수 있습니다.");
            return;
        }
        if (!SystemTray.isSupported()) {
            showError("이 환경에서는 작업 표시줄 알림 영역을 사용할 수 없습니다.");
            return;
        }

        try {
            connectWindowsFunctions();
            // Ctrl+Q가 이미 등록돼 있으면 두 번째 실행 또는 다른 프로그램과의 충돌을 알린다.
            int modifiers = MOD_CONTROL | MOD_NOREPEAT;
            int registered;
            int errorCode;
            try (Arena errorArena = Arena.ofConfined()) {
                MemoryLayout errorLayout = Linker.Option.captureStateLayout();
                MemorySegment capturedError = errorArena.allocate(errorLayout);
                registered = (int) registerHotKey.invoke(
                    capturedError, MemorySegment.NULL, HOTKEY_ID, modifiers, VK_Q);
                long errorOffset = errorLayout.byteOffset(
                    MemoryLayout.PathElement.groupElement("GetLastError"));
                errorCode = capturedError.get(ValueLayout.JAVA_INT, errorOffset);
            }
            if (registered == 0) {
                if (errorCode == ERROR_HOTKEY_ALREADY_REGISTERED) {
                    showError("이미 실행 중이거나 Ctrl+Q를 다른 프로그램이 사용 중입니다.");
                } else {
                    showError("단축키 Ctrl+Q를 등록하지 못했습니다.\nWindows 오류 코드: " + errorCode);
                }
                return;
            }

            System.out.println("한영키 오입력 복원 시험 프로그램 실행 중");
            System.out.println("편집 가능한 글자를 선택하고 Ctrl+Q를 누르세요.");
            TrayIcon trayIcon = null;
            try (Arena arena = Arena.ofConfined()) {
                trayIcon = addTrayIcon((int) getCurrentThreadId.invoke());
                // 64비트 Windows의 MSG 구조체는 48바이트이며 message 값은 8바이트 위치에 있다.
                MemorySegment message = arena.allocate(48, 8);
                while (true) {
                    int result = (int) getMessage.invoke(
                        message, MemorySegment.NULL, 0, 0);
                    if (result <= 0) break;
                    if (message.get(ValueLayout.JAVA_INT, 8) == WM_HOTKEY) {
                        processHotkey();
                    }
                }
            } finally {
                // 종료 요청이나 오류로 메시지 루프를 벗어나면 등록한 자원을 해제한다.
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                unregisterHotKey.invoke(MemorySegment.NULL, HOTKEY_ID);
            }
        } catch (Throwable error) {
            showError("Windows 연결 중 오류가 발생했습니다: " + error.getMessage());
            error.printStackTrace(System.err);
        }
    }

    /** 창 없는 실행 파일에서도 시작 실패 원인을 보여준다. */
    private static void showError(String message) {
        System.err.println(message);
        JOptionPane.showMessageDialog(null, message,
            "한영키 오입력 복원", JOptionPane.ERROR_MESSAGE);
    }

    /** 숨겨진 아이콘 영역에 실행 표시와 종료 메뉴를 만든다. */
    private static TrayIcon addTrayIcon(int mainThreadId) throws AWTException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(31, 96, 180));
            graphics.fillRoundRect(0, 0, 32, 32, 8, 8);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("Malgun Gothic", Font.BOLD, 22));
            graphics.drawString("한", 4, 25);
        } finally {
            graphics.dispose();
        }

        MenuItem exitItem = new MenuItem("종료");
        exitItem.addActionListener(event -> {
            try {
                // 메시지 대기를 끝내면 main의 finally에서 단축키와 아이콘을 정리한다.
                postThreadMessage.invoke(mainThreadId, WM_QUIT, 0L, 0L);
            } catch (Throwable error) {
                System.err.println("종료 요청을 전달하지 못했습니다: " + error.getMessage());
            }
        });
        PopupMenu menu = new PopupMenu();
        menu.add(exitItem);
        TrayIcon trayIcon = new TrayIcon(image, "한영키 오입력 복원 (Ctrl+Q)", menu);
        trayIcon.setImageAutoSize(true);
        SystemTray.getSystemTray().add(trayIcon);
        return trayIcon;
    }

    /** 선택 영역 복사 → 입력 검증 → 한영 변환 → 원래 편집 창에 붙여넣기를 수행한다. */
    private static void processHotkey() {
        try {
            // 1. 작업을 시작한 창과 키 해제 상태를 확인한다.
            // 창이 바뀌면 다른 프로그램에 결과를 붙여넣을 수 있으므로 취소한다.
            long originalWindow = ((MemorySegment) getForegroundWindow.invoke()).address();
            if (originalWindow == 0) {
                System.out.println("활성 창을 확인할 수 없습니다.");
                return;
            }

            // 사용자가 누른 Ctrl과 Q가 자동 복사·붙여넣기에 섞이지 않도록 기다린다.
            if (!waitUntilHotkeyReleased() || !isOriginalWindow(originalWindow)) {
                System.out.println("단축키를 놓지 않았거나 활성 창이 바뀌어 취소했습니다.");
                return;
            }

            Robot robot = new Robot();
            robot.setAutoDelay(20);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 2. 복사 실패 시 되돌릴 수 있도록 기존 클립보드 내용을 보관한다.
            Transferable previous = clipboard.getContents(null);
            if (previous != null && previous.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                // 기존 글자는 클립보드가 바뀐 뒤에도 복원할 수 있도록 별도로 보관한다.
                previous = new StringSelection(
                    (String) previous.getTransferData(DataFlavor.stringFlavor));
            }

            // 3. 임시 표식을 넣은 뒤 Ctrl+C를 보낸다. 표식이 바뀌면 새 복사가 끝난 것이다.
            String marker = "HanYeongRecovery-" + System.nanoTime();
            setClipboardContents(clipboard, new StringSelection(marker));
            String original = null;
            try {
                sendShortcut(robot, KeyEvent.VK_C);
                original = waitForCopiedText(clipboard, marker);
            } finally {
                if (original == null) restoreClipboard(clipboard, previous);
            }

            if (original == null || !isOriginalWindow(originalWindow)) {
                System.out.println("선택한 글자를 복사하지 못했거나 활성 창이 바뀌었습니다.");
                return;
            }

            // 4. 선택하지 않은 VS Code 줄이나 Excel 셀은 끝에 줄바꿈이 붙어 복사될 수 있다.
            // 이를 붙여넣으면 예상 밖의 줄이 추가될 수 있으므로 변환을 거절한다.
            if (original.endsWith("\n") || original.endsWith("\r")) {
                System.out.println("줄바꿈으로 끝나는 복사 내용은 변환하지 않습니다. 글자만 선택하세요.");
                return;
            }

            // 5. 변환 클래스가 문자 종류를 판별하고 두벌식 규칙으로 변환한다.
            String converted;
            try {
                converted = KoreanEnglishRecovery.convert(original);
            } catch (IllegalArgumentException invalidInput) {
                System.out.println("변환하지 않았습니다: " + invalidInput.getMessage());
                return;
            }

            if (!isOriginalWindow(originalWindow)) {
                System.out.println("활성 창이 바뀌어 붙여넣기를 취소했습니다.");
                return;
            }

            // 6. 시작한 창이 그대로 활성화돼 있을 때만 결과를 붙여넣는다.
            setClipboardContents(clipboard, new StringSelection(converted));
            robot.delay(60);
            sendShortcut(robot, KeyEvent.VK_V);
            System.out.println("변환 결과를 붙여넣었습니다.");
        } catch (IllegalStateException | AWTException error) {
            System.out.println("처리하지 못했습니다: " + error.getMessage());
        } catch (Throwable error) {
            System.out.println("Windows 입력 처리 중 오류가 발생했습니다: " + error.getMessage());
        }
    }

    /** 클립보드의 임시 표식이 선택한 글자로 바뀔 때까지 최대 1초간 기다린다. */
    private static String waitForCopiedText(Clipboard clipboard, String marker)
        throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                    if (!marker.equals(text)) return text;
                }
            } catch (IllegalStateException | UnsupportedFlavorException | IOException retry) {
                // 다른 프로그램이 클립보드를 사용 중이면 잠시 후 다시 읽는다.
            }
            Thread.sleep(20);
        }
        return null;
    }

    /** 다른 프로그램이 클립보드를 잠깐 사용 중이면 저장을 최대 20번 재시도한다. */
    private static void setClipboardContents(Clipboard clipboard, Transferable contents)
        throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                clipboard.setContents(contents, null);
                return;
            } catch (IllegalStateException retry) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("클립보드에 결과를 저장할 수 없습니다.");
    }

    /** 선택한 글자를 복사하지 못했으면 이전 클립보드 내용을 되돌린다. */
    private static void restoreClipboard(Clipboard clipboard, Transferable previous) {
        try {
            setClipboardContents(clipboard,
                previous == null ? new StringSelection("") : previous);
        } catch (InterruptedException | IllegalStateException error) {
            System.out.println("이전 클립보드 내용을 복원하지 못했습니다: " + error.getMessage());
        }
    }

    /** 작업을 시작한 편집 창이 계속 활성 상태인지 확인한다. */
    private static boolean isOriginalWindow(long originalWindow) throws Throwable {
        return ((MemorySegment) getForegroundWindow.invoke()).address() == originalWindow;
    }

    /** Ctrl과 지정한 키를 누르고, 오류가 나더라도 두 키를 놓는다. */
    private static void sendShortcut(Robot robot, int key) {
        try {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(key);
        } finally {
            robot.keyRelease(key);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
    }

    /** Ctrl과 Q를 모두 놓을 때까지 최대 2초 동안 기다린다. */
    private static boolean waitUntilHotkeyReleased() throws Throwable {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!isKeyDown(KeyEvent.VK_CONTROL) && !isKeyDown(VK_Q)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    /** Windows가 보고하는 현재 키 눌림 상태를 확인한다. */
    private static boolean isKeyDown(int virtualKey) throws Throwable {
        short state = (short) getAsyncKeyState.invoke(virtualKey);
        return (state & 0x8000) != 0;
    }
}
