# Android SKK for Physical Keyboard

伝統的な SKK (Simple Kana Kanji conversion) の操作体系を Android 上で再現した入力メソッドです。
Emacs の [DDSKK](https://github.com/skk-dev/ddskk) に近い操作感を提供します。

物理キーボードでの操作に特化していますが、ソフトウェアキーボードとして QWERTY、記号バー、およびストローク入力も搭載しています。
ストローク入力は、後述のパブリックな仕様書に基づき、Palm V のGraffiti風のストロークで入力するインターフェースです。

Unihertz Titan や Clicks Keyboard などの、直接物理キーボードから入力できない文字がある機種に対して
小さな画面キーボードを表示するようにしています。
画面キーボードに配置する文字（記号等）は、設定画面から GUI で好みのレイアウトにカスタマイズすることが可能です（ストローク入力を除く）。

SKK 変換エンジンは minghai 氏によるオリジナル版と海月玲二氏による SKK for Android のハードキー版を参考にしています。

## インストール
この GitHub ページの Releases からダウンロードしてインストールしてください。

Ctrl キーでの操作が必須なので、Unihertz Titan ではシステムの設定からショートカットで Fn キーなどに Ctrl キーを割り当ててください。

## ユーザーマニュアル
> [!NOTE]
> このユーザーマニュアルは Android Studio の Gemini Agent により、実際の実装コードをもとに生成されています。

### 1. モードの切り替え
- **かな・カナ切替**: `q`
    - ひらがな → カタカナ (→ 半角カタカナ ※設定有効時) の順でトグルします。
- **英数モード (ASCII)**: `l`
    - 半角英数入力に切り替わります。
- **全角英数モード**: `L`
    - 全角英数入力に切り替わります。
- **Abbrev モード**: `/`
    - アルファベットを直接入力して、英単語から漢字への変換などがおこなえます。
- **ひらがなに戻る**: `Ctrl-j`
    - どのモード・状態からでも、ひらがなモード（通常状態）へ戻ります。

### 2. 漢字変換
#### 基本的な流れ
1.  **読みの入力 (▽)**: `Shift` キーを押しながら最初の文字を入力します（例: `Shift-K` `a` `n` `j` `i` → `▽かんじ`）。
2.  **送り仮名の入力**: 読みの途中で `Shift` キーを押しながら送り仮名の最初の文字を入力します（例: `Shift-U` `k` `a` `Shift-T` `a` → `▽うか*た`）。
3.  **変換の実行**: `Space` キーを押すと、第1候補が表示されます（▼）。
4.  **候補の選択**: `Space` キーや `→` キーで次の候補、`x` キーや `←` キーで前の候補を選択します。
5.  **確定**: `Enter` キー、または次の文字を入力することで選択中の候補を確定します。

#### 応用的な変換操作
- **再変換**: `Ctrl-u` または `Ctrl-g` (直接入力時)
    - 直前に確定した単語を未確定状態（変換中）に戻してやり直します。
- **接頭辞・接尾辞入力**: `>` / `<` / `?`
    - 見出し語入力の開始時にこれらの記号を入力することで、接頭辞・接尾辞として辞書検索をおこないます。
- **継続変換（接尾語）**: `>` (変換中)
    - 候補を選択した状態で `>` を入力すると、その候補を確定した直後に接尾語（`>`）を付与した状態で見出し語入力を開始します。
- **動的候補**: `today`, `now`, `date`, `time` 等のキーワード
    - 見出し語入力中（▽）にこれらのキーワードを入力して `>` を押すと、現在の日時や時刻、和暦（令和等）を変換候補として表示します。
- **カタカナ確定**: `q` (見出し語入力中)
    - 入力中の読み（▽）を、変換を介さずそのままカタカナとして確定します。

#### 数値変換
辞書に登録された数値テンプレート（#0〜#5）に基づき、入力された数値を以下の形式に変換して表示します。
- **#0**: そのまま (例: 123)
- **#1**: 全角 (例: １２３)
- **#2**: 漢数字 (例: 一二三)
- **#3**: 位取りあり漢数字 (例: 百二十三)
- **#4**: 位取りあり漢数字・旧字体 (例: 壱百弐拾参)
- **#5**: 位取りあり混合表記 (例: 1億2345万6789)

### 3. 入力補完
- **補完の開始**: `Tab` または `Ctrl-i`
    - 見出し語入力中（▽）または Abbrev モード中に、辞書にある単語から補完候補を提示します。
- **補完の確定**: `.` (ピリオド)
    - 補完候補が選択されている状態でピリオドを押すと、その候補を確定します。

### 4. 個人辞書と学習
- **ユーザー辞書学習の有効/無効**:
    - 設定画面から、変換確定時の単語学習（順位の記憶や新規登録）を制御できます。
    - **無効時**: 候補選択をおこなっても学習されません。また、候補リストの末尾まで達した際に自動で単語登録モードへ移行せず、最初の候補に戻るようになります。
- **候補の削除**: `X` (Shift-x)
    - 変換中に `X` を押すと、現在選択している候補をユーザー辞書から削除できます。
- **辞書ツール**:
    - 設定画面の「辞書」から、登録された単語を一覧形式で確認・削除できます。

### 5. 状態別キー操作一覧

#### 通常モード（直接入力）
| キー操作 | 機能 |
| :--- | :--- |
| `q` | かな・カナ切替（ひらがな→カタカナ→半角カナ） |
| `l` | 英数（ASCII）モードへ切替 |
| `L` | 全角英数モードへ切替 |
| `/` | Abbrevモード（英単語漢字変換）へ切替 |
| `>` / `<` / `?` | 接頭辞・接尾辞トリガーによる見出し語入力開始 |
| `Ctrl-u` | 再変換（直前の確定を取り消して変換し直す） |
| `Ctrl-j` | ひらがなモードへ復帰 |

#### 見出し語入力中（▽）
| キー操作 | 機能                               |
| :--- |:---------------------------------|
| `Space` | 変換開始                             |
| `大文字 (Shift + Key)` | 送り仮名入力の開始（例：`Shift-T` で「▽...*た」） |
| `q` | 現在の読みをカタカナで確定                    |
| `>` / `<` / `?` | 動的候補の表示 / 接頭辞・接尾辞を伴う変換開始      |
| `Ctrl-q` | 読みのかな/カナ切替                       |
| `Tab` / `Ctrl-i` | 補完の開始・次候補選択                      |
| `.` | 選択中の補完候補を確定                      |

#### Abbrevモード
| キー操作 | 機能 |
| :--- | :--- |
| `Space` | 変換開始 |
| `Ctrl-q` | 入力中の英単語を全角英数で確定 |
| `Tab` / `Ctrl-i` | 補完の開始・次候補選択 |
| `.` | 選択中の補完候補を確定 |

#### 変換候補選択中（▼）
| キー操作 | 機能 |
| :--- | :--- |
| `Space` / `→` (DPAD) | 次の候補へ移動 |
| `x` / `←` (DPAD) | 前の候補へ移動 |
| `>` | 現在の候補を確定し、続けて接尾語（`>`）見出し語入力を開始 |
| `Ctrl-n` / `Ctrl-f` | 次の候補へ移動 |
| `Ctrl-p` / `Ctrl-b` | 前の候補へ移動 |
| `X` (Shift-x) | 現在選択中の候補を個人辞書から削除 |

#### 全般・共通操作
| キー操作 | 機能 |
| :--- | :--- |
| `Enter` | 現在の入力を確定 / 改行の挿入 |
| `Backspace` / `Ctrl-w` | 1文字削除 / 状態のキャンセル（変換中止など） |
| `Ctrl-j` | どの状態からでも「ひらがなモード」の通常状態へ復帰 |
| `Ctrl-g` | 入力の中断・キャンセル |

### 6. 設定オプション
- **句読点の切り替え**: 設定から `。、` (標準) と `．，` (学術・公用文向け) を選択可能です。
- **入力形式による自動切替**: URI 入力欄などで自動的に英数モードへ切り替える設定が可能です。
- **状態表示**: `▽` `▼` などの状態インジケータの表示/非表示を切り替えられます。
- **半角カタカナ**: カナトグル（`q`）に半角カタカナを含めるかどうかを設定できます。
- **SandS (Space and Shift)**: スペースキーを単独で押すとスペース、他のキーと同時に押すと Shift キーとして機能させる設定です。
- **ツールチップ**: モード変更時の通知表示時間を調整できます。
- **バイブレーション**: 画面キーボード操作時の触覚フィードバックを有効にできます。
- **候補表示位置**: 記号バー使用時に、候補リストをバーの上に重ねるか、独立して表示するかを選択できます。
- **バックアップ・復元**: 設定内容の保存と読み込みが可能です。

### 7. ローマ字かな変換表
詳細なローマ字かな変換ルールについては、[ROMAJI.md](ROMAJI.md) を参照してください。

### 8. ソフトウェアキーボード 
物理キーボードの有無や好みに合わせて、画面上のキーボード表示を切り替えられます。

- **表示の切り替え**: 設定の「キーボードの種類」から「QWERTY」「記号バー」「ストローク」を選択できます。
  - 物理キーボードがない場合はデフォルトで **QWERTY** が選択されます。
  - 物理キーボードがある場合は、1行の記号入力補助である **記号バー** がデフォルトです。
- **ストローク入力**:
  - 画面をなぞって文字を入力するGraffiti風の手書き入力方式です。画面左側がアルファベット、右側が数字に対応しています。
  - 画面から上の外側へのストロークでヘルプ画面を表示することができます。ヘルプ画面をタップでページ切り替え、ヘルプ画面外をタップで入力エリアに戻ります。
- **QWERTY / 記号バーの操作**:
  - `Shift`: 大文字入力（SKK の読み開始など）に使用します。**2回連続で押すとロック状態**になり、解除するにはもう一度押します。
  - `Ctrl`: `Ctrl-j` や `Ctrl-a` などの入力に使用します。一度押すと「ON」状態になり、次のキー入力に Ctrl が適用されます。
  - `Sym`: 記号のレイアウトに切り替えます。こちらも**2回連続で押すとロック状態**になり、記号入力を継続できます。解除するにはもう一度押すか、他のモードキー（Shift 等）を押します。
- **カスタマイズ**:
  - 設定画面から、各モード（通常・Shift・記号）のキー配置をドラッグ＆ドロップ操作で自由に編集可能です。
  - 各キーのウェイト（幅）や、特定の機能を割り当てた「機能キー」の配置も GUI 上で調整できます。

## 技術的背景および仕様の準拠 (Technical Background & Specification)
本IMEにおける「ストローク入力」の文字認識パターンおよび入力ロジックの基本仕様は、
以下のパブリックなドキュメントに基づいて設計・実装されています。

- **参照ドキュメント**: 『Handbook for the Palm V™ Organizer』
- **参照セクション**: "Entering Data in Your Palm V™ Organizer"

上記の仕様書に定義されている一画入力（Graffiti）のストローク形状をベースとしつつ、
現代のAndroid OSのIME APIおよびディスプレイ環境に適合するよう、
座標検出および判定アルゴリズムを独自にスクラッチから実装しています。

ソースコードが非公開の既存の商用アプリケーション（株式会社ACCESS製「Graffiti Pro」等）のリバースエンジニアリングはおこなっていません。

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
### 同梱の SKK 辞書について
本ソフトウェアに同梱されているシステム辞書データは、以下のプロジェクトより提供されている辞書ファイル（L 辞書等）をもとに作成されています。
- **SKK 辞書**: [SKK Open Dictionary License](https://github.com/skk-dev/dict/blob/master/LICENSE) (GPL compatible)

同梱にあたっては、原版から注釈（annotation）を取り除いた上で、独自の jdbm 形式に変換して使用しています。

SKK 辞書データは GNU GPL ライセンスに基づいて配布されていますが、本ソフトウェアにおける辞書の使用は「データの参照」にとどまります。
GPL における著作物の解釈において「プログラム（コード）」と「データ」は明確に区別されます。
本ソフトウェアは辞書データを単なる外部データとして読み込んで処理する「道具」として動作するため、データの著作権がプログラム本体に波及することはありません。
これは FSF（フリーソフトウェア財団）の見解や「単なる集合体（Mere Aggregation）」の原則に基づいた判断であり、
本ソフトウェアのソースコードおよびバイナリ配布は GPL ライセンスの影響を受けることなく、
独立した Apache License 2.0 のもとで配布・利用が可能です。

### Sudachi 辞書
本ソフトウェアのシステム辞書の一部（語彙、頻度情報等）は、株式会社ワークスアプリケーションズにより公開されている Sudachi 辞書をもとに作成されています。
- **Sudachi 辞書**: [https://github.com/WorksApplications/SudachiDict]
- **Licenses**: [https://github.com/WorksApplications/SudachiDict#licenses]

### 商標・権利関係について
- 「Palm」「Palm V」「Graffiti® 」および「Graffiti® for Android™」は、該当する権利者（株式会社ACCESS等）の商標または登録商標です。
- 本プロジェクトは個人開発による独立したオープンソース実装であり、権利者各社とは組織的・商業的な関係を一切有しません。

## 免責事項
- **無保証**: 本ソフトウェアは「現状のまま」提供され、明示的・暗示的を問わず、いかなる種類の保証（商品性、特定の目的への適合性、および権利の非侵害に関する保証を含むがこれらに限定されない）もおこないません。
- **責任の限定**: 本ソフトウェアの使用または使用不能から生じるいかなる損害（入力データの損失、業務の中断、デバイスの故障など）について、作者および著作権者は一切の責任を負いません。
- **自己責任**: 本ソフトウェアのインストールおよび利用は、すべて利用者の自己責任においておこなってください。
- **開発状況**: 本プロジェクトは個人による開発途上のプログラムであり、予期せぬ動作や不具合が含まれる可能性があります。
- **プライバシーについて**: 本 IME は、ユーザーが入力した情報を開発者が意図的に外部へ送信する機能は備えていません。しかし、オープンソースソフトウェアとして公開されているため、利用者は各自の責任においてソースコードを確認し、安全性を判断してください。
