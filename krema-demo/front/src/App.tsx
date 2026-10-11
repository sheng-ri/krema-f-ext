/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
import { useCallback, useEffect, useState } from "react";
import { getNote, updateNote } from "./notes";

/**
 * 前后端状态同步的最小界面：读一条文本、编辑它、写回后端。
 *
 * <p>状态刻意只留在后端：组件不另存一份副本，保存后以后端返回的值为准。
 * 这样「状态放哪」在 demo 里只有一个答案 —— 看 {@link NoteCommands}。
 */
export default function App() {
  const [text, setText] = useState("");
  const [status, setStatus] = useState("读取中…");

  useEffect(() => {
    getNote()
      .then((note) => {
        setText(note);
        setStatus("已从后端读取");
      })
      .catch((err: unknown) => setStatus(`读取失败：${String(err)}`));
  }, []);

  const save = useCallback(() => {
    setStatus("写入中…");
    updateNote(text)
      .then((saved) => {
        setText(saved);
        setStatus("已写回后端");
      })
      .catch((err: unknown) => setStatus(`写入失败：${String(err)}`));
  }, [text]);

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 24 }}>
      <h1 style={{ fontSize: 20 }}>Krema Demo</h1>
      <p style={{ color: "#666" }}>这条文本存在 Java 后端的字段里，刷新页面后仍在。</p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={6}
        style={{ width: "100%", fontFamily: "monospace" }}
      />
      <div style={{ marginTop: 12, display: "flex", gap: 12, alignItems: "center" }}>
        <button onClick={save}>写回后端</button>
        <span style={{ color: "#666" }}>{status}</span>
      </div>
    </main>
  );
}
