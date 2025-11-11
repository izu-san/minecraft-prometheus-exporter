# Grafanaダッシュボード設定ガイド

## データソースの設定方法

### 方法1: インポート時のマッピング（推奨）

1. **Grafanaにログイン**
   - ブラウザでGrafanaにアクセスしてログインします

2. **Prometheusデータソースを作成（まだ作成していない場合）**
   - 左メニューの ⚙️ **Configuration** → **Data sources** をクリック
   - **Add data source** をクリック
   - **Prometheus** を選択
   - **URL** にPrometheusサーバーのURLを入力（例: `http://localhost:9090`）
   - **Save & test** をクリックして接続を確認
   - データソース名をメモしておきます（例: "Prometheus"）

3. **ダッシュボードをインポート**
   - 左メニューの 📊 **Dashboards** → **Import** をクリック
   - **Upload JSON file** で `grafana-dashboard.json` をアップロード、または
   - **Import via panel json** にJSONの内容を貼り付け
   - **Load** をクリック

4. **データソースをマッピング**
   - インポート画面の下部に「**Prometheus**」というセクションが表示されます
   - ドロップダウンから、先ほど作成したPrometheusデータソースを選択します
   - **Import** をクリック

これで完了です！ダッシュボードが表示されます。

### 方法2: JSONファイルを直接編集する方法

もしインポート画面でデータソースのマッピングが表示されない場合、JSONファイルを直接編集できます：

1. **PrometheusデータソースのUIDを確認**
   - Grafanaで ⚙️ **Configuration** → **Data sources** → **Prometheus** を開く
   - URLの最後の部分（例: `/datasources/edit/xxxxx`）の `xxxxx` がUIDです
   - または、データソース設定画面の下部に「**UID**」が表示されています

2. **JSONファイルを編集**
   - `grafana-dashboard.json` を開く
   - `"uid": "${DS_PROMETHEUS}"` を検索して全て見つける
   - 全てを `"uid": "あなたのPrometheusデータソースのUID"` に置き換える
   - 例: `"uid": "prometheus-uid-12345"`

3. **編集したJSONをインポート**
   - 編集したJSONファイルをGrafanaにインポートします

## トラブルシューティング

### データソースが見つからないエラーが出る場合

- Prometheusデータソースが正しく作成されているか確認してください
- データソースのUIDが正しいか確認してください
- JSONファイル内の全ての `"uid": "${DS_PROMETHEUS}"` が置き換えられているか確認してください

### メトリクスが表示されない場合

- PrometheusがMinecraftサーバーのメトリクスエンドポイント（デフォルト: `http://サーバーIP:9225/metrics`）をスクレイプしているか確認してください
- Prometheusの設定ファイル（`prometheus.yml`）に以下のような設定があるか確認してください：

```yaml
scrape_configs:
  - job_name: 'minecraft-server'
    static_configs:
      - targets: ['サーバーIP:9225']
```

- Prometheusのターゲットページで、Minecraftサーバーが「UP」状態になっているか確認してください

## 参考情報

- Grafana公式ドキュメント: https://grafana.com/docs/grafana/latest/dashboards/export-import/
- Prometheusデータソース設定: https://grafana.com/docs/grafana/latest/datasources/prometheus/

