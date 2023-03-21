package com.mybatis.plugin.nofullupdate;

/**
 * 防全表更新插件异常类
 */
public class NoFullUpdatePluginException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoFullUpdatePluginException(String message) {
        super(message);
    }

    public NoFullUpdatePluginException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
