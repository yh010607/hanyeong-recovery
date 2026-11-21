/** 변환 방향, 겹자모, 거절 입력을 확인하는 개발용 검사. 배포 JAR에는 넣지 않는다. */
public class KoreanEnglishRecoveryTest {
    public static void main(String[] args) {
        // 영문 → 한글과 한글 → 영문 기본 변환 및 문장부호
        check("gksrmf", "한글");
        check("한글", "gksrmf");
        check("dkssudgktpdy!", "안녕하세요!");
        check("안녕하세요!", "dkssudgktpdy!");
        check("rhk", "과");
        check("과", "rhk");
        check("dkswkd", "안장");
        check("안장", "dkswkd");
        check("hello", "ㅗ디ㅣㅐ");
        check("ㅗ디ㅣㅐ", "hello");

        // Shift로 입력한 쌍자음과 겹받침
        check("rRk", "ㄱ까");
        check("ㄱ까", "rRk");
        check("R", "ㄲ");
        check("ㄲ", "R");
        check("rkqt", "값");
        check("값", "rkqt");

        // 문자 종류 혼합, 변환할 문자 없음, 지원하지 않는 문자
        checkRejected("");
        checkRejected("gksrmf 한글");
        checkRejected("123");
        checkRejected("abc🙂");
        System.out.println("변환 검사 20건 통과");
    }

    private static void check(String input, String expected) {
        String actual = KoreanEnglishRecovery.convert(input);
        if (!expected.equals(actual)) {
            throw new AssertionError(input + " -> " + actual + "; expected " + expected);
        }
    }

    private static void checkRejected(String input) {
        try {
            KoreanEnglishRecovery.convert(input);
            throw new AssertionError("Expected rejection: " + input);
        } catch (IllegalArgumentException expected) {
            // 예외가 발생하면 예상대로 거절된 것이다.
        }
    }
}
