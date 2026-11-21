// 두벌식 영문 키와 한글 자모를 서로 찾기 위한 대응표
import java.util.HashMap; // 영문 키와 한글 자모의 대응표를 저장할 자료구조
import java.util.Map; // 대응표를 다룰 때 사용하는 인터페이스

/**
 * 한영키 상태를 잘못 설정해 입력한 글자를 복원하는 변환 클래스다.
 *
 * 이 클래스는 문자 변환만 담당한다. Windows 단축키와 붙여넣기는 WindowsClipboardRecovery가 담당한다.
 * 입력에 영문 또는 한글이 있는지 확인한 뒤 영문은 한글로, 한글은 영문 키로 바꾼다.
 */
public class KoreanEnglishRecovery {
    // 완성형 한글 음절을 조합하거나 분해할 때 쓰는 유니코드 순서다.
    // 초성 19개, 중성 21개, 종성 28개를 순서대로 적었다.
    // 종성 목록의 첫 번째 값은 받침이 없는 경우를 뜻한다.
    private static final String INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String FINALS = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    // KEY_TO_JAMO: 영문 키 → 자모. JAMO_TO_KEYS: 자모 → 영문 키.
    // Shift로 달라지는 자모와 두 키로 입력하는 겹자모도 아래에서 추가한다.
    private static final Map<Character, Character> KEY_TO_JAMO = new HashMap<>();
    private static final Map<Character, String> JAMO_TO_KEYS = new HashMap<>();

    // 프로그램 시작 시 두벌식 기본 키와 겹자모 대응표를 한 번 만든다.
    static {
        String keys = "rsefaqtdwczxvgkioOjupPhynbml";
        String jamo = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎㅏㅑㅐㅒㅓㅕㅔㅖㅗㅛㅜㅠㅡㅣ";
        for (int i = 0; i < keys.length(); i++) {
            KEY_TO_JAMO.put(keys.charAt(i), jamo.charAt(i));
            JAMO_TO_KEYS.put(jamo.charAt(i), String.valueOf(keys.charAt(i)));
        }

        // 두벌식 자판에서 Shift로 입력하는 쌍자음과 모음을 추가한다.
        addShift('R', 'ㄲ');
        addShift('E', 'ㄸ');
        addShift('Q', 'ㅃ');
        addShift('T', 'ㅆ');
        addShift('W', 'ㅉ');

        // 겹모음과 겹받침은 키를 두 번 눌러야 하므로 별도로 등록한다.
        JAMO_TO_KEYS.put('ㄳ', "rt");
        JAMO_TO_KEYS.put('ㄵ', "sw");
        JAMO_TO_KEYS.put('ㄶ', "sg");
        JAMO_TO_KEYS.put('ㄺ', "fr");
        JAMO_TO_KEYS.put('ㄻ', "fa");
        JAMO_TO_KEYS.put('ㄼ', "fq");
        JAMO_TO_KEYS.put('ㄽ', "ft");
        JAMO_TO_KEYS.put('ㄾ', "fx");
        JAMO_TO_KEYS.put('ㄿ', "fv");
        JAMO_TO_KEYS.put('ㅀ', "fg");
        JAMO_TO_KEYS.put('ㅄ', "qt");
        JAMO_TO_KEYS.put('ㅘ', "hk");
        JAMO_TO_KEYS.put('ㅙ', "ho");
        JAMO_TO_KEYS.put('ㅚ', "hl");
        JAMO_TO_KEYS.put('ㅝ', "nj");
        JAMO_TO_KEYS.put('ㅞ', "np");
        JAMO_TO_KEYS.put('ㅟ', "nl");
        JAMO_TO_KEYS.put('ㅢ', "ml");
    }

    /** Shift가 필요한 두벌식 키와 한글 자모의 대응 관계를 등록한다. */
    private static void addShift(char key, char jamo) {
        KEY_TO_JAMO.put(key, jamo);
        JAMO_TO_KEYS.put(jamo, String.valueOf(key));
    }

    /** 한글과 영문이 섞였거나 변환할 글자가 없으면 INVALID를 반환한다. */
    private static Direction detectDirection(String input) {
        boolean latin = false;
        boolean korean = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (isLatin(ch)) {
                latin = true;
            } else if (isKorean(ch)) {
                korean = true;
            } else if (!isAllowedNeutral(ch)) {
                return Direction.INVALID;
            }
            if (latin && korean) {
                return Direction.INVALID;
            }
        }
        if (latin) {
            return Direction.TO_KOREAN;
        }
        if (korean) {
            return Direction.TO_ENGLISH;
        }
        return Direction.INVALID;
    }

    /** 문자가 영문 알파벳이면 참을 반환한다. */
    private static boolean isLatin(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /** 문자가 완성형 한글 음절 또는 한글 호환 자모이면 참을 반환한다. */
    private static boolean isKorean(char ch) {
        return (ch >= '\uAC00' && ch <= '\uD7A3') ||
               (ch >= '\u3131' && ch <= '\u3163');
    }

    /** 숫자, 기본 문장부호, 공백처럼 변환하지 않고 유지할 문자인지 확인한다. */
    private static boolean isAllowedNeutral(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' ||
               (ch >= '0' && ch <= '9') ||
               (ch >= '!' && ch <= '/') || (ch >= ':' && ch <= '@') ||
               (ch >= '[' && ch <= '`') || (ch >= '{' && ch <= '~');
    }

    /** 입력 전체의 방향을 정해 변환한다. 혼합·미지원 입력은 예외로 거절한다. */
    public static String convert(String input) {
        Direction direction = detectDirection(input);
        if (direction == Direction.INVALID) {
            throw new IllegalArgumentException(
                "한글 또는 영문만 입력하세요. 두 문자 종류를 섞으면 변환하지 않습니다.");
        }
        return direction == Direction.TO_KOREAN ? toKorean(input) : toEnglish(input);
    }

    /** 각 영문 키를 자모로 바꾸고 Composer로 완성형 한글을 만든다. */
    private static String toKorean(String input) {
        StringBuilder output = new StringBuilder();
        Composer composer = new Composer(output);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (isLatin(ch)) {
                // Shift로 다른 자모가 나오는 키가 아니면 대문자도 소문자와 같은 자모로 처리한다.
                Character jamo = KEY_TO_JAMO.get(ch);
                if (jamo == null) {
                    jamo = KEY_TO_JAMO.get(Character.toLowerCase(ch));
                }
                composer.accept(jamo);
            } else {
                // 문장부호와 공백을 만나면 조합 중인 음절을 먼저 확정한다.
                composer.flush();
                output.append(ch);
            }
        }
        composer.flush();
        return output.toString();
    }

    /** 한글 음절을 초성·중성·종성으로 분해해 각 자모의 영문 키를 잇는다. */
    private static String toEnglish(String input) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= '\uAC00' && ch <= '\uD7A3') {
                // 완성형 음절은 초성 19 × 중성 21 × 종성 28 순서로 저장된다.
                int syllable = ch - '\uAC00';
                char initial = INITIALS.charAt(syllable / 588);
                char vowel = VOWELS.charAt((syllable % 588) / 28);
                char last = FINALS.charAt(syllable % 28);
                appendKeys(output, initial);
                appendKeys(output, vowel);
                if (last != '\0') {
                    appendKeys(output, last);
                }
            } else if (isKorean(ch)) {
                appendKeys(output, ch);
            } else {
                output.append(ch);
            }
        }
        return output.toString();
    }

    /** 한글 자모 하나에 해당하는 영문 키 입력을 결과에 추가한다. */
    private static void appendKeys(StringBuilder output, char jamo) {
        String keys = JAMO_TO_KEYS.get(jamo);
        if (keys == null) {
            throw new IllegalArgumentException("지원하지 않는 한글 자모: " + jamo);
        }
        output.append(keys);
    }

    /** 연속으로 입력한 두 모음이 겹모음이 될 수 있으면 그 겹모음을 반환한다. */
    private static char combineVowel(char first, char second) {
        String pair = "" + first + second;
        switch (pair) {
            case "ㅗㅏ": return 'ㅘ';
            case "ㅗㅐ": return 'ㅙ';
            case "ㅗㅣ": return 'ㅚ';
            case "ㅜㅓ": return 'ㅝ';
            case "ㅜㅔ": return 'ㅞ';
            case "ㅜㅣ": return 'ㅟ';
            case "ㅡㅣ": return 'ㅢ';
            default: return '\0';
        }
    }

    /** 연속으로 입력한 두 자음이 겹받침이 될 수 있으면 그 겹받침을 반환한다. */
    private static char combineFinal(char first, char second) {
        String pair = "" + first + second;
        switch (pair) {
            case "ㄱㅅ": return 'ㄳ';
            case "ㄴㅈ": return 'ㄵ';
            case "ㄴㅎ": return 'ㄶ';
            case "ㄹㄱ": return 'ㄺ';
            case "ㄹㅁ": return 'ㄻ';
            case "ㄹㅂ": return 'ㄼ';
            case "ㄹㅅ": return 'ㄽ';
            case "ㄹㅌ": return 'ㄾ';
            case "ㄹㅍ": return 'ㄿ';
            case "ㄹㅎ": return 'ㅀ';
            case "ㅂㅅ": return 'ㅄ';
            default: return '\0';
        }
    }

    /** 겹받침을 다음 음절로 나눌 때 앞 음절에 남을 자음을 구한다. */
    private static char firstOfFinal(char last) {
        String keys = JAMO_TO_KEYS.get(last);
        if (keys != null && keys.length() == 2) {
            return KEY_TO_JAMO.get(keys.charAt(0));
        }
        return '\0';
    }

    /** 겹받침을 다음 음절로 나눌 때 다음 음절로 이동할 자음을 구한다. */
    private static char secondOfFinal(char last) {
        String keys = JAMO_TO_KEYS.get(last);
        if (keys != null && keys.length() == 2) {
            return KEY_TO_JAMO.get(keys.charAt(1));
        }
        return '\0';
    }

    /** 유니코드 공식: 가 + (초성 번호 × 21 + 중성 번호) × 28 + 종성 번호. */
    private static char syllable(char initial, char vowel, char last) {
        int initialIndex = INITIALS.indexOf(initial);
        int vowelIndex = VOWELS.indexOf(vowel);
        int finalIndex = last == '\0' ? 0 : FINALS.indexOf(last);
        return (char) ('\uAC00' + (initialIndex * 21 + vowelIndex) * 28 + finalIndex);
    }

    /** 영문 키를 읽으며 아직 완성되지 않은 초성·중성·종성을 관리한다. */
    private static final class Composer {
        private final StringBuilder output;
        // 값이 없을 때는 '\0'을 사용한다. 받침이 다음 음절로 옮겨질 수 있어 즉시 출력하지 않는다.
        private char initial;
        private char vowel;
        private char last;

        Composer(StringBuilder output) {
            this.output = output;
        }

        /** 자모 하나를 받아 현재 음절에 붙이고, 붙일 수 없으면 새 음절을 시작한다. */
        void accept(char jamo) {
            boolean consonant = INITIALS.indexOf(jamo) >= 0;
            if (!consonant) {
                acceptVowel(jamo);
            } else if (initial == '\0') {
                // 첫 자음은 새 음절의 초성이 된다.
                initial = jamo;
            } else if (vowel == '\0') {
                // 모음 없이 자음이 연속되면 앞 자음을 먼저 출력한다.
                flush();
                initial = jamo;
            } else if (last == '\0') {
                // 이미 초성과 중성이 있으면 자음을 받침으로 붙여 본다.
                if (FINALS.indexOf(jamo) >= 0) {
                    last = jamo;
                } else {
                    flush();
                    initial = jamo;
                }
            } else {
                // 받침이 있는 상태에서 자음이 더 오면 겹받침을 만들 수 있는지 확인한다.
                char joined = combineFinal(last, jamo);
                if (joined != '\0') {
                    last = joined;
                } else {
                    flush();
                    initial = jamo;
                }
            }
        }

        /** 새 모음이 오면 겹모음을 만들거나, 앞 음절 받침을 다음 초성으로 옮긴다. */
        private void acceptVowel(char next) {
            if (initial == '\0') {
                output.append(next);
            } else if (vowel == '\0') {
                vowel = next;
            } else if (last == '\0') {
                char joined = combineVowel(vowel, next);
                if (joined != '\0') {
                    vowel = joined;
                } else {
                    flush();
                    output.append(next);
                }
            } else {
                // 받침 뒤에 모음이 오면 받침의 마지막 자음을 다음 음절 초성으로 옮긴다.
                char first = firstOfFinal(last);
                char moved = secondOfFinal(last);
                if (first != '\0') {
                    output.append(syllable(initial, vowel, first));
                } else {
                    moved = last;
                    output.append(syllable(initial, vowel, '\0'));
                }
                initial = moved;
                vowel = next;
                last = '\0';
            }
        }

        /** 공백·문장부호·입력 끝에서 진행 중인 음절을 출력하고 상태를 비운다. */
        void flush() {
            if (initial != '\0') {
                output.append(vowel == '\0' ? initial : syllable(initial, vowel, last));
            }
            initial = vowel = last = '\0';
        }
    }

    /** 변환 방향 또는 변환할 수 없는 입력 상태를 나타낸다. */
    private enum Direction { TO_KOREAN, TO_ENGLISH, INVALID }

}
