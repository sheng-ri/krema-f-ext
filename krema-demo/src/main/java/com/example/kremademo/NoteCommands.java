/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package com.example.kremademo;

import build.krema.core.KremaCommand;

/**
 * 前后端状态同步的最小示例：前端读一条文本，也能改它。
 *
 * <p>{@link KremaCommand} 的注解值就是命令名，前端拿这个名字调；注解处理期据此生成
 * {@code krema-commands.d.ts}，前端因此对参数与返回值都有类型。
 *
 * <p>状态用普通字段而非静态变量：命令对象由主类 {@code new} 出来、全进程只有一个，
 * 字段就是那份状态本身，不需要再为「存在哪」写一层单例。
 */
public final class NoteCommands {
    private String note = "还没写过内容";

    /** 读后端状态。前端 {@code krema.invoke('get')} 拿到这里的返回值。 */
    @KremaCommand("get")
    public String get() {
        return note;
    }

    /** 改后端状态并回写新值，前端拿返回值确认落库后的内容。 */
    @KremaCommand("update")
    public String update(String text) {
        note = text;
        return note;
    }
}