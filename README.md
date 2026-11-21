# 한영키 오입력 복원 프로그램

한영키를 바꾸는 것을 잊고 글을 입력했을 때, 잘못 입력한 부분을 지우고 다시 쓰는 대신 선택해서 바로 고치는 Windows 프로그램이다. 선택한 글자가 영문인지 한글인지에 따라 변환 방향을 자동으로 정한다.

## 실행 환경과 파일

이 GitHub 저장소에는 소스 코드와 빌드 방법이 들어 있다. 실행 파일은 저장소에 포함되어 있지 않다. Windows 11 64비트에서 확인했으며, 별도로 받은 `HanYeongRecovery.zip`이 있다면 한 폴더에 압축 해제하고 폴더 구조를 유지한 채 `HanYeongRecovery.exe`를 실행한다. 배포 폴더에 `runtime`이 포함되어 있어 EXE 실행에 Java를 따로 설치할 필요는 없다. 소스에서 직접 만드는 방법은 아래 「소스에서 빌드·검사」에 적었다. `HanYeongRecovery-Windows.jar`를 따로 실행하려면 Java 25가 필요하다.

```powershell
java --enable-native-access=ALL-UNNAMED -jar HanYeongRecovery-Windows.jar
```

## 사용 방법

1. 프로그램을 실행하고 작업 표시줄의 파란색 `한` 아이콘이 나타나는지 확인한다. 보이지 않으면 숨겨진 아이콘 표시를 연다.
2. 글을 쓰다가 한영키를 잘못 눌렀다면, 바꾸고 싶은 부분을 마우스로 선택한다. 다른 글자나 줄바꿈은 선택하지 않는다.
3. `Ctrl+Q`를 누른다. 프로그램이 선택한 글자를 한글 또는 영문으로 바꿔 같은 자리에 넣는다. 잘못 입력한 단어나 문장의 일부에도 같은 방법을 쓸 수 있다.
4. 사용을 마치면 `한` 아이콘을 오른쪽 클릭하고 `종료`를 선택한다.

## 지원 범위와 제한

- 영문 알파벳 또는 완성형 한글·호환 자모를 변환한다. ASCII 숫자·기본 문장부호·공백은 변환 대상 글자와 함께 있을 때 유지한다. 숫자나 기호만 있는 입력, 한영 혼합, 다른 언어는 거절한다.
- VS Code에서 아무것도 선택하지 않고 `Ctrl+Q`를 누르면 현재 줄과 줄바꿈이 복사되는 경우가 있다. 이 버전은 끝에 줄바꿈이 있는 복사 결과를 거절한다. 따라서 줄바꿈까지 선택한 경우도 변환되지 않는다.
- Excel에서 셀만 선택하면 복사한 글자 끝에 줄바꿈이 붙어 변환이 거절된다. `F2`로 셀 편집을 시작한 뒤 바꿀 글자만 선택한다.
- Word에서는 문단 기호까지 선택하면 변환 후 다음 문단과 합쳐질 수 있다. 문단 기호를 제외하고 바꿀 글자만 선택한다. 다른 앱에서도 선택 영역은 반드시 확인한다.
- 한글→영문 변환은 원래 입력의 대소문자를 추정하지 못한다. 같은 자모로 입력된 키는 기본 영문 키로 복원하고, 쌍자음처럼 Shift로 다른 자모가 된 키는 대문자로 대응한다.
- 변환을 취소하거나 처리 중 오류가 나면 이전 클립보드를 가능한 범위에서 되돌린다. 변환이 성공하면 클립보드에 변환 결과가 남는다. 모든 서식의 보존은 보장하지 않는다.
- 읽기 전용 PDF 본문처럼 붙여넣을 수 없는 화면에서는 사용할 수 없다.
- `Ctrl+Q`는 Excel의 빠른 분석 기능 및 VS Code의 기본 기능과 충돌한다. 프로그램이 실행 중이면 이 단축키는 변환 요청으로 처리된다.
- 프로그램이 이미 실행 중이거나 다른 프로그램이 `Ctrl+Q`를 전역 단축키로 등록했다면 `이미 실행 중이거나 Ctrl+Q를 다른 프로그램이 사용 중입니다.`라는 안내가 나온다.

## 사용 범위 및 예시

글자를 선택하고 다시 입력할 수 있는 화면에서 사용한다. 한 줄 안의 단어 또는 문장 중 잘못 입력한 부분을 선택하면 나머지 글자는 그대로 둔다. 다음은 사용할 수 있는 작업의 예다.

| 작업 | 사용 예시 |
| --- | --- |
| 메모장에 글쓰기 | 문장을 쓰는 중 입력 언어를 잘못 선택했다면 해당 부분만 선택해 고친다. |
| 문서·발표 자료 작성 | Word 문서나 PowerPoint 슬라이드의 제목, 본문, 설명 문구를 편집할 때 사용한다. |
| 표 작업 | Excel에서 `F2`로 셀 편집을 시작한 뒤 셀 안의 글자만 선택해 고친다. |
| 코드 작성 | VS Code에서 주석이나 문자열을 입력하다 생긴 한영키 실수를 고친다. |
| 웹 검색 | Chrome의 Google 검색창에 잘못 입력한 검색어를 선택해 고친다. |

한글을 쓰려다 영문으로 입력한 경우와 영문을 쓰려다 한글로 입력한 경우 모두 사용할 수 있다. 앱마다 글자를 선택하는 방식이 다르므로 위의 「지원 범위와 제한」도 확인한다.

## 소스 코드 구성

| 파일 | 역할 |
| --- | --- |
| `KoreanEnglishRecovery.java` | 두벌식 키와 한글 자모의 대응, 한글 음절 조합·분해 |
| `WindowsClipboardRecovery.java` | 전역 단축키, 선택 영역 복사·교체, 트레이 아이콘 |
| `KoreanEnglishRecoveryTest.java` | 변환 결과와 거절 입력 20건 검사. 배포 JAR에서는 제외 |

## 소스에서 빌드·검사

Windows PowerShell과 Java 25 JDK에서 이 폴더를 연 뒤 실행한다.

```powershell
New-Item -ItemType Directory -Force build,test-build,input | Out-Null
javac -encoding UTF-8 -d build KoreanEnglishRecovery.java WindowsClipboardRecovery.java
javac -encoding UTF-8 -cp build -d test-build KoreanEnglishRecoveryTest.java
java -cp "build;test-build" KoreanEnglishRecoveryTest
jar --create --file HanYeongRecovery-Windows.jar --main-class WindowsClipboardRecovery -C build .
Copy-Item HanYeongRecovery-Windows.jar input
jpackage --type app-image --name HanYeongRecovery --input input --main-jar HanYeongRecovery-Windows.jar --java-options "--enable-native-access=ALL-UNNAMED"
```

검사가 성공하면 `변환 검사 20건 통과`가 표시된다. `jpackage` 명령은 실행 파일과 Java 런타임을 담은 `HanYeongRecovery` 폴더를 만든다.
