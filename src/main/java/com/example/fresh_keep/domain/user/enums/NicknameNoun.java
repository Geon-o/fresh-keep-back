package com.example.fresh_keep.domain.user.enums;

// 익명 유저에게 자동 배정되는 랜덤 닉네임의 명사 부분.
// 닉네임은 최대 8자 제한이라, 형용사(NicknameAdjective) 최대 길이(4자)와 합쳐 8자를 넘지 않도록
// 여기 등록하는 값은 전부 4자 이하로 유지한다.
public enum NicknameNoun {
    BROWNIE("브라우니"),
    AVOCADO("아보카도"),
    BROCCOLI("브로콜리"),
    MACARON("마카롱"),
    PUDDING("푸딩"),
    JELLY("젤리"),
    MUFFIN("머핀"),
    SALAD("샐러드"),
    SANDWICH("샌드위치"),
    BANANA("바나나"),
    BLUEBERRY("블루베리"),
    PEACH("복숭아"),
    MELON("멜론"),
    MANGO("망고"),
    APPLE("사과"),
    STRAWBERRY("딸기"),
    CHERRY("체리"),
    SHERBET("샤베트");

    private final String label;

    NicknameNoun(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
