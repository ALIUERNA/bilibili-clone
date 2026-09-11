"""
把 tools/bat-src 里的 UTF-8 源文件编译成项目根目录下的 .bat（GBK 编码）。

为什么要这么做：
    Windows 的 cmd.exe 默认代码页是 936（GBK 中文），
    如果 .bat 文件存成 UTF-8，里面的中文会被解析成乱码，
    甚至导致 echo、括号块解析失败。所以这里统一转成 GBK。

用法： python tools/bat-src/build_bats.py
"""
import pathlib
import sys

SRC = pathlib.Path(__file__).parent
ROOT = SRC.parent.parent


def main():
    files = sorted(SRC.glob('*.bat.txt'))
    if not files:
        print('没有找到源文件')
        return 1
    for src in files:
        text = src.read_text(encoding='utf-8')
        text = text.replace('\r\n', '\n').replace('\n', '\r\n')
        target = ROOT / src.name[:-4]          # 去掉结尾的 .txt
        target.write_bytes(text.encode('gbk'))
        print(f'已生成 {target.name}  ({len(text.encode("gbk"))} 字节, GBK/CRLF)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
