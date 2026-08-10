package com.example.fresh_keep.domain.user.enums;

// 익명 유저에게 자동 배정되는 랜덤 닉네임의 형용사 부분.
// 닉네임은 최대 8자 제한이라, 명사(NicknameNoun) 최대 길이(4자)와 합쳐 8자를 넘지 않도록
// 여기 등록하는 값은 전부 4자 이하로 유지한다.
public enum NicknameAdjective {
    FLUFFY("뽀송뽀송"),
    LUSH("파릇파릇"),
    CHEWY("쫀득쫀득"),
    CRISP("아삭아삭"),
    BOUNCY("탱글탱글"),
    SWEET_TART("새콤달콤"),
    NUTTY("고소한"),
    SWEET("달콤한"),
    COOL("시원한"),
    SOFT("말랑말랑"),
    CRUNCHY("바삭바삭"),
    COZY("포근한"),
    FRESH("싱싱한"),
    TANGY("상큼한"),
    SUGARY("달달한"),
    TENDER("부드러운"),
    FRESH_BAKED("갓구운"),
    FRESH_PICKED("갓수확한"),
    MOIST("촉촉한"),
    WARM("따끈따끈");

    private final String label;

    NicknameAdjective(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
