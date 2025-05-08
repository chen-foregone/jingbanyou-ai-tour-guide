package cn.edu.gdou.jingbanyou.common.enums;

import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;

/**
 * 游客端错误码枚举
 *
 * @author jingbanyou
 */
public enum TouristErrorCode {

    T001("T001", "景区ID不能为空"),
    T002("T002", "景区不存在"),
    T003("T003", "会话不存在"),
    T004("T004", "消息内容不能为空"),
    T005("T005", "visitorId不能为空"),
    T006("T006", "sessionId不能为空"),
    T007("T007", "音频文件不能为空"),
    T008("T008", "语音识别失败"),
    T009("T009", "文本不能为空"),
    T010("T010", "语音合成失败"),
    T011("T011", "处理失败"),
    T500("T500", "系统内部错误");

    private final String code;
    private final String message;

    TouristErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        String template = message;
        for (Object arg : args) {
            template = template.replaceFirst("\\{}", arg.toString());
        }
        return template;
    }

    /**
     * 构建错误 AjaxResult
     */
    public AjaxResult toAjaxResult() {
        return AjaxResult.error(message);
    }

    /**
     * 构建带参数的错误 AjaxResult
     */
    public AjaxResult toAjaxResult(Object... args) {
        return AjaxResult.error(getMessage(args));
    }
}
