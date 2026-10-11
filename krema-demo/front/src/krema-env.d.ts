/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */

/**
 * krema 桥接由原生侧在运行时注入（window.krema），前端拿不到它的模块文件 ——
 * 所以这里只声明「window 上有这个东西」，具体类型取自 processor 生成的命令表。
 *
 * <p>写成 typeof krema 而不是自己抄一份签名：命令表变了（增删命令、改参数），
 * 这个声明自动跟着变，不会出现「类型定义与后端脱节」。
 */
import type { krema } from "./krema-commands";

declare global {
  interface Window {
    krema: typeof krema;
  }
}

export {};
