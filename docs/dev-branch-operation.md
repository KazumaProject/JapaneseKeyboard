# dev ブランチ運用ガイド

この文書では、このリポジトリを `dev` ブランチ中心で開発し、`main` ブランチからリリースするための手順を説明します。

## 1. ブランチの役割

~~~text
feature/*、fix/*、docs/*、chore/*
                  ↓ Pull Request
                 dev
                  ↓ リリース用Pull Request
                main
                  ↓ vX.Y.Z タグ
             GitHub Release
~~~

### `main`

- リリース可能な状態を保つブランチです。
- 通常の開発では直接コミットしません。
- リリース時のタグは `main` のコミットに付けます。
- `v*` タグをpushすると、GitHub ActionsがリリースAPKをビルドしてGitHub Releaseを作成します。

### `dev`

- 次のリリースに含める変更を統合するブランチです。
- 通常の機能追加・バグ修正のPull Requestの送信先です。
- `dev` に直接コミットせず、作業ブランチからPull Requestを作成します。

### 作業ブランチ

作業内容に応じて、次のような名前を使用します。

~~~text
feature/image-button
fix/popup-input-behavior
docs/dev-branch-operation
chore/update-gradle
hotfix/release-crash
~~~

## 2. 最初の準備

すでにリポジトリをclone済みの場合は、リモートの情報を更新します。

~~~bash
git fetch origin
git switch dev
git pull --ff-only origin dev
~~~

`dev` がローカルにない場合は、次のように作成します。

~~~bash
git fetch origin
git switch --track origin/dev
~~~

現在のブランチと作業ツリーを確認します。

~~~bash
git status --short --branch
~~~

作業開始時には、次の状態になっていることを確認してください。

- 現在のブランチが `dev`
- `origin/dev` と同期している
- 未コミットの変更がない

## 3. 通常の開発手順

### 3.1 `dev` を最新化する

作業ブランチを作る前に、必ず `dev` を最新化します。

~~~bash
git switch dev
git pull --ff-only origin dev
~~~

### 3.2 作業ブランチを作る

~~~bash
git switch -c feature/変更内容
~~~

例:

~~~bash
git switch -c feature/add-image-button
~~~

作業ブランチは、原則として1つの目的に限定します。機能追加、無関係なリファクタリング、フォーマット変更などを1つのPull Requestに混在させないでください。

### 3.3 変更を確認する

変更前後に、次のコマンドで対象ファイルと差分を確認します。

~~~bash
git status
git diff
git diff --check
~~~

不要な生成物、署名ファイル、`local.properties`、モデルファイルをコミットしないでください。

### 3.4 ローカルで確認する

現在のPull Request用CIは、処理時間を短くするため `check` のみを実行します。コードのビルドやテストは自動では行われないため、変更内容に応じてローカルで確認します。

通常のアプリ変更では、軽量版Debugビルドを実行します。

~~~bash
./gradlew :app:assembleLiteStandardDebug
~~~

Kotlin・Javaのロジックを変更した場合は、単体テストも実行します。

~~~bash
./gradlew :app:testLiteStandardDebugUnitTest
~~~

AndroidリソースやManifest、Lint対象の変更を行った場合は、Lintを実行します。

~~~bash
./gradlew :app:lintLiteStandardDebug
~~~

複数の確認をまとめて実行する場合は、次のコマンドを使用します。

~~~bash
./gradlew \
  :app:assembleLiteStandardDebug \
  :app:testLiteStandardDebugUnitTest \
  :app:lintLiteStandardDebug \
  --stacktrace \
  --no-daemon \
  --max-workers=2
~~~

Full版、Zenz、入力処理、IMEサービスに関係する変更では、必要に応じて追加確認を行います。

~~~bash
./gradlew :app:assembleFullStandardDebug
~~~

Full版のビルドではZenzモデルの準備とネイティブコードのビルドが行われます。初回はモデルの取得に時間がかかる場合があります。

入力処理の回帰確認は、GitHub Actionsの `Fast Input Regression` ワークフローを手動実行します。全ケースの実行には時間がかかるため、入力処理に関係する変更やリリース前に実行してください。

### 3.5 コミットする

変更内容が確認できたら、意図したファイルだけをstageします。

~~~bash
git status
git add path/to/changed-file
git diff --cached
git commit -m "Add image button"
~~~

コミットメッセージは、変更内容を短い英語の命令形で記述します。例:

~~~text
Add image button to markdown toolbar
Fix popup input behavior
Update F-Droid release metadata
~~~

### 3.6 リモートへpushする

~~~bash
git push -u origin feature/add-image-button
~~~

2回目以降のpushは、通常次のコマンドで行えます。

~~~bash
git push
~~~

共有済みの作業ブランチに対してforce pushを行わないでください。

## 4. Pull Requestの作成

GitHubでPull Requestを作成するときは、次の設定にします。

- base repository: `KazumaProject/JapaneseKeyboard`
- base branch: `dev`
- compare branch: 自分の作業ブランチ

通常の開発でbaseを `main` にしてはいけません。`main` へのPull Requestはリリースまたは緊急修正のときだけ作成します。

### Pull Request本文に書く内容

最低限、次の項目を記載します。

~~~markdown
## 変更内容
- 何を変更したか
- なぜ変更したか

## 確認内容
- [ ] ./gradlew :app:assembleLiteStandardDebug
- [ ] ./gradlew :app:testLiteStandardDebugUnitTest
- [ ] ./gradlew :app:lintLiteStandardDebug

## 補足
- 画面変更がある場合はスクリーンショット
- 関連Issueや既知の制限
~~~

### Pull Request作成後

1. CIの `up-to-date / check` が完了することを確認します。
2. レビュー指摘に対応します。
3. 修正を同じ作業ブランチへpushします。
4. CIが再実行されたことを確認します。
5. 承認後、`dev` へマージします。

CIは現在、リポジトリのcheckoutと `echo "ok"` のみを実行します。CIが成功しても、ビルドやテストが成功したことを意味しません。必要なローカル確認結果をPull Request本文に記載してください。

### マージ方法

通常の機能追加や修正は、Pull Request画面の **Squash and merge** を使用します。これにより、`dev` の履歴を機能単位で整理できます。

マージ後は、不要になったリモート作業ブランチを削除します。ローカルでは次のように更新します。

~~~bash
git switch dev
git pull --ff-only origin dev
git branch -d feature/add-image-button
~~~

## 5. リリース手順

### 5.1 リリース前の確認

`dev` に次のリリースに含める変更がすべて入っていることを確認します。

- 必要なPull Requestがすべてマージ済み
- 既知の重大な不具合がない
- 必要なローカルビルド・テストが完了している
- 入力処理に関係する場合はFast Input Regressionを実行済み
- `app/build.gradle` の `versionCode` と `versionName` が更新済み

バージョン更新も通常の変更として、まず `dev` へPull Requestを作成します。

### 5.2 `dev` から `main` へPull Requestを作成する

GitHubで次のPull Requestを作成します。

~~~text
base: main
compare: dev
~~~

タイトル例:

~~~text
Release v1.7.105
~~~

このPull Requestで、リリース対象の変更内容とバージョン番号を最終確認します。

### 5.3 `main` にマージする

レビュー後、`dev` から `main` へマージします。マージ後、ローカルの `main` を最新化します。

~~~bash
git fetch origin
git switch main
git pull --ff-only origin main
~~~

### 5.4 リリースタグを作成する

タグは必ず最新の `origin/main` を確認した後に作成します。

~~~bash
git tag -a v1.7.105 -m "Release v1.7.105"
git push origin v1.7.105
~~~

`v` で始まるタグをpushすると、`.github/workflows/android.yml` が次のRelease APKをビルドしてGitHub Releaseへアップロードします。

- Full Standard Release
- Lite Standard Release
- Lite F-Droid Release

GitHub Actionsの実行結果とGitHub Releaseの添付ファイルを確認してください。署名情報やHugging Faceの設定が必要なため、リリースCIに必要なRepository Secretsが登録されていることも確認します。

### 5.5 リリース後に `dev` を同期する

リリース後、`main` にだけ存在する変更があれば、`main` から `dev` へのPull Requestを作成して同期します。

~~~text
base: dev
compare: main
~~~

通常、リリース対象の変更はすでに `dev` に存在するため、追加の同期が不要な場合もあります。

## 6. 緊急修正の手順

リリース済みの `main` に対して緊急修正が必要な場合は、`main` から作業ブランチを作成します。

~~~bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c hotfix/fix-crash
~~~

修正後は次の順番で進めます。

1. `hotfix/*` から `main` へPull Requestを作成する。
2. レビュー後、`main` へマージする。
3. バージョン番号を更新し、必要ならタグを作成する。
4. 同じ修正を `dev` にも反映する。
5. `main` から `dev` へのPull Request、または同じ修正内容のPull Requestを作成する。

`main` だけを修正して、`dev` へ戻し忘れないようにしてください。

## 7. 現在のGitHub Actions

### `up-to-date.yml`

- `dev` または `main` を対象とするPull Requestで実行されます。
- `dev` または `main` へのpushでも実行されます。
- 現在は `check` ジョブでcheckoutと `echo "ok"` のみを行います。
- Androidのビルド、単体テスト、Lintは自動実行しません。

### `android.yml`

- `v*` タグのpushで実行されます。
- リリース用の署名付きAPKをビルドします。
- GitHub Releaseを作成し、APKをアップロードします。

### `fast-input-regression.yml`

- GitHub Actions画面から手動実行します。
- Pixel 6 Pro、API 35のエミュレータで入力回帰テストを実行します。
- 実行範囲、回数、スクリーンショット取得の有無を入力で指定できます。
- 入力処理の変更時とリリース前に実行します。

## 8. ブランチ保護の推奨設定

GitHubのBranch protectionまたはRulesetで、`dev` と `main` に次の設定を行います。

### `dev`

- Pull Request必須
- `up-to-date / check` の成功を必須化
- force push禁止
- ブランチ削除禁止

### `main`

- Pull Request必須
- `up-to-date / check` の成功を必須化
- レビュー1名以上を必須化
- force push禁止
- ブランチ削除禁止

設定後も、通常の開発者が直接 `dev` や `main` にpushできないことを確認してください。

## 9. やってはいけないこと

- 通常の機能追加を `main` へ直接pushする
- 作業ブランチから `main` へ直接Pull Requestを送る
- `dev` や `main` をforce pushする
- 署名ファイル、`local.properties`、秘密情報をコミットする
- ローカル確認をせずに「CIが通ったので問題ない」と判断する
- リリースタグを `dev` の未確認コミットに付ける
- `main` だけに緊急修正を入れて、`dev` へ反映しない

## 10. よく使うコマンド一覧

~~~bash
# devを最新化
git fetch origin
git switch dev
git pull --ff-only origin dev

# 作業ブランチを作成
git switch -c feature/example

# 差分を確認
git status
git diff
git diff --check

# 軽量版をビルド
./gradlew :app:assembleLiteStandardDebug

# 単体テスト
./gradlew :app:testLiteStandardDebugUnitTest

# Lint
./gradlew :app:lintLiteStandardDebug

# 作業ブランチをpush
git push -u origin feature/example

# mainを最新化
git switch main
git pull --ff-only origin main

# リリースタグを作成・push
git tag -a v1.7.105 -m "Release v1.7.105"
git push origin v1.7.105
~~~

