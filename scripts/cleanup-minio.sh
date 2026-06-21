#!/bin/bash
# 清空 MinIO notes bucket，避免测试数据污染
# 用法: bash scripts/cleanup-minio.sh

echo "正在清空 MinIO notes 和 uploads bucket ..."

docker exec minio mc alias set local http://localhost:9000 minioadmin minioadmin 2>/dev/null

# 清空 notes bucket（递归删除所有对象）
docker exec minio mc rm --recursive --force local/notes/ 2>/dev/null
echo "  notes bucket 已清空"

# 清空 uploads bucket
docker exec minio mc rm --recursive --force local/uploads/ 2>/dev/null
echo "  uploads bucket 已清空"

echo "完成！"
