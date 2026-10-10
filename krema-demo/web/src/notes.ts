/*
 * 前端侧的 krema 契约来自 processor 生成的 krema-commands.d.ts：
 * 命令名与参数类型由它约束，写错命令名或参数类型在 tsc 阶段就报出来。
 *
 * <p>类型走 import type（.d.ts 没有运行时产物），值走 window.krema ——
 * 桥接由原生侧注入，dev 与打包两种形态下都是同一个全局。
 */
import type { krema } from "./krema-commands";

const api: typeof krema = window.krema;

/** 读后端当前保存的文本。 */
export async function getNote(): Promise<string> {
  return api.invoke("get");
}

/** 把新文本交给后端存下；以后端返回的值为准，不假设写入必然成功。 */
export async function updateNote(text: string): Promise<string> {
  return api.invoke("update", { text });
}
