#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""通过 GitHub Git Data API 推送本地 HEAD 到远程。

为什么需要这个脚本：本机环境阻断 github.com 主站（git push 走 HTTPS 会
Connection reset），但 api.github.com 可达。Git Data API 允许直接创建
blob / tree / commit 并更新分支引用，效果等价于一次 push。

用法：python tools/push_via_api.py
"""
from __future__ import annotations

import base64
import json
import subprocess
import sys
import time

REPO = "linmumua/BuildingWand"
BRANCH = "main"


def gh(args: list[str], payload: dict | None = None) -> dict:
    """调用 gh api。"""
    cmd = ["gh", "api"] + args
    if payload is not None:
        cmd += ["--input", "-"]
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    # 网络偶发 401/5xx/超时，最多重试 5 次
    for attempt in range(5):
        proc = subprocess.run(cmd, input=data, capture_output=True)
        if proc.returncode == 0:
            break
        time.sleep(2 * (attempt + 1))
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr.decode("utf-8", "replace"))
        raise SystemExit(f"gh api 失败: {' '.join(cmd)}")
    out = proc.stdout.decode("utf-8", "replace").strip()
    return json.loads(out) if out else {}


def local_files() -> list[str]:
    """取 git 已跟踪的全部文件（与 .gitignore 一致）。"""
    proc = subprocess.run(["git", "ls-files", "-z"], capture_output=True)
    if proc.returncode != 0:
        raise SystemExit("git ls-files 失败，请确认在仓库根目录运行")
    return [p for p in proc.stdout.decode("utf-8").split("\0") if p]


def blob_sha(path: str) -> str:
    """把本地文件内容写成远程 blob，返回 sha。

    【必须按 git 的存储形式上传】仓库里源码以 LF 保存（见 .gitattributes），
    而 Windows 工作区磁盘上是 CRLF。直接上传原文会让远程与本地 git 索引
    的哈希对不上，之后正常 push 时 git 会把这些文件全都当成改动。
    """
    with open(path, "rb") as handle:
        data = handle.read()
    if path.endswith(".bat"):
        data = data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")
    elif not path.endswith((".jar", ".png", ".zip", ".bbmodel")):
        data = data.replace(b"\r\n", b"\n")
    encoded = base64.b64encode(data).decode("ascii")
    result = gh([f"repos/{REPO}/git/blobs", "--method", "POST"],
                {"content": encoded, "encoding": "base64"})
    return result["sha"]


def main() -> None:
    files = local_files()
    print(f"共 {len(files)} 个文件，开始上传 blob…")

    entries = []
    for index, path in enumerate(files, 1):
        sha = blob_sha(path)
        entries.append({
            "path": path.replace("\\", "/"),
            "mode": "100755" if path.endswith((".sh",)) else "100644",
            "type": "blob",
            "sha": sha,
        })
        if index % 10 == 0 or index == len(files):
            print(f"  {index}/{len(files)}")

    # gradlew 需要可执行位
    for entry in entries:
        if entry["path"] == "gradlew":
            entry["mode"] = "100755"

    print("创建 tree…")
    # base_tree 指向当前分支最新 commit 的 tree：这样只提交差异，
    # 而且初始化阶段用 Contents API 建的 README 不会被丢掉。
    ref = gh([f"repos/{REPO}/git/ref/heads/{BRANCH}"])
    parent_sha = ref["object"]["sha"]
    parent = gh([f"repos/{REPO}/git/commits/{parent_sha}"])
    tree = gh([f"repos/{REPO}/git/trees", "--method", "POST"],
              {"tree": entries, "base_tree": parent["tree"]["sha"]})

    message = subprocess.run(
        ["git", "log", "-1", "--pretty=%B"], capture_output=True
    ).stdout.decode("utf-8").strip()

    print("创建 commit…")
    commit = gh([f"repos/{REPO}/git/commits", "--method", "POST"], {
        "message": message,
        "tree": tree["sha"],
        "parents": [parent_sha],
    })

    print("更新分支引用…")
    gh([f"repos/{REPO}/git/refs/heads/{BRANCH}", "--method", "PATCH"], {
        "sha": commit["sha"],
    })

    print(f"完成：{commit['sha']}")


if __name__ == "__main__":
    main()
