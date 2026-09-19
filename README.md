# Android SKK for Physical Keyboard

伝統的な SKK (Simple Kana Kanji conversion) の操作体系を Android 上で再現した入力メソッドです。
Emacs の [DDSKK](https://github.com/skk-dev/ddskk) に近い操作感を提供します。

物理キーボードでの操作に特化していますが、ソフトウェアキーボードとして
QWERTY、タブレット、記号バー、絵文字パレット、およびストローク入力を搭載しています。

Unihertz Titan や Clicks Keyboard などの、直接物理キーボードから入力できない文字がある機種に対して
小さな補助記号キーボードを表示することができます。

画面キーボードに配置する文字（記号等）は、設定画面から GUI で好みのレイアウトにカスタマイズすることが可能です。

直接入力したひらがなを後から再変換する機能があります。ひらがなで殴り書きした文を後から変換できます。

SKK 変換エンジンは minghai 氏によるオリジナル版と海月玲二氏による SKK for Android のハードキー版を参考にしています。

## 辞書データについて

本アプリの組み込み辞書は、Sudachi 辞書から生成された語句で構築されています。

オリジナルの SKK 辞書（`SKK-JISYO.L` など）を利用したい場合は、
辞書ファイルを端末ローカルにダウンロードの上、設定画面の辞書ツールからインポートしてください。

インポートした追加辞書はユーザーの学習辞書とは分離して別管理されるため、
学習履歴を汚さずに巨大な辞書を追加・クリア・再インポートすることが可能です。
主な辞書のダウンロードは設定画面からダウンロードできます。

※ 本アプリはセキュリティ・プライバシー保護のためネットワーク接続権限（`INTERNET`）を保有していません。

## インストール

この GitHub ページの Releases からダウンロードしてインストールしてください。

Ctrl キーでの操作が必須なので、Unihertz Titan では
システムの設定からショートカットで Fn キーなどに Ctrl キーを割り当ててください。

## ユーザーマニュアル

詳細な操作方法については、[MANUAL.md](MANUAL.md) を参照してください。

## 技術的背景および仕様の準拠

本IMEにおける「ストローク入力」の文字認識パターンおよび入力ロジックの基本仕様は、
以下のパブリックなドキュメントに基づいて設計・実装されています。

- **参照ドキュメント**: 『Handbook for the Palm V™ Organizer』
- **参照セクション**: "Entering Data in Your Palm V™ Organizer"

ソースコードが非公開の既存の商用アプリケーション（株式会社ACCESS製「Graffiti Pro」等）の
リバースエンジニアリングはおこなっていません。

## ライセンス等

### 本ソフトウェアについて

本ソフトウェアのソースコードは Apache License, Version 2.0 に基づいて公開されています。

```
Copyright (c) 2026 kachaya

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### jdbm-1.0

本ソフトウェアは、辞書エンジンのバックエンドとして jdbm-1.0 を使用しています。
https://jdbm.sourceforge.net/

```
/**
 * JDBM LICENSE v1.00
 *
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "JDBM" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of Cees de Groot.  For written permission,
 *    please contact cg@cdegroot.com.
 *
 * 4. Products derived from this Software may not be called "JDBM"
 *    nor may "JDBM" appear in their names without prior written
 *    permission of Cees de Groot. 
 *
 * 5. Due credit should be given to the JDBM Project
 *    (http://jdbm.sourceforge.net/).
 *
 * THIS SOFTWARE IS PROVIDED BY THE JDBM PROJECT AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * CEES DE GROOT OR ANY CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2000 (C) Cees de Groot. All Rights Reserved.
 * Contributions are Copyright (C) 2000 by their associated contributors.
 *
 * $Id: LICENSE.txt,v 1.1 2000/05/05 23:59:52 boisvert Exp $
 */
```

### Sudachi 辞書

本ソフトウェアのシステム辞書の一部（語彙、頻度情報等）は、株式会社ワークスアプリケーションズにより公開されている Sudachi 辞書をもとに作成されています。

- **Sudachi 辞書**: [https://github.com/WorksApplications/SudachiDict]
- **Licenses**: [https://github.com/WorksApplications/SudachiDict#licenses]

### UniDic

本ソフトウェアのシステム辞書の生成のためにSudachi辞書で使われているunidic-mecab-2.1.2の品詞情報ファイル(left-id.def)を使用しています。

- **unidic-mecab-2.1.2**: [https://clrd.ninjal.ac.jp/unidic_archive/cwj/2.1.2/]

### 商標・権利関係について

- 「Palm」「Palm V」「Graffiti® 」および「Graffiti® for Android™」は、該当する権利者（株式会社ACCESS等）の商標または登録商標です。
- 本プロジェクトは個人開発による独立したオープンソース実装であり、権利者各社とは組織的・商業的な関係を一切有しません。

## 免責事項

- **無保証**: 本ソフトウェアは「現状のまま」提供され、明示的・暗示的を問わず、いかなる種類の保証（商品性、特定の目的への適合性、および権利の非侵害に関する保証を含むがこれらに限定されない）もおこないません。
- **責任の限定**: 本ソフトウェアの使用または使用不能から生じるいかなる損害（入力データの損失、業務の中断、デバイスの故障など）について、作者および著作権者は一切の責任を負いません。
- **自己責任**: 本ソフトウェアのインストールおよび利用は、すべて利用者の自己責任においておこなってください。
- **開発状況**: 本プロジェクトは個人による開発途上のプログラムであり、予期せぬ動作や不具合が含まれる可能性があります。
- **プライバシーについて**: 本 IME は、ユーザーが入力した情報を開発者が意図的に外部へ送信する機能は備えていません。しかし、オープンソースソフトウェアとして公開されているため、利用者は各自の責任においてソースコードを確認し、安全性を判断してください。
