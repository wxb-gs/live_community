#!/usr/bin/env python3
"""
清空所有测试数据 —— 包括 Cassandra（笔记/评论）、MySQL（用户/互动）、
Elasticsearch（搜索索引）、MinIO（图片）。

用法:
    python scripts/cleanup-all.py

或只清空特定部分:
    python scripts/cleanup-all.py --cassandra-only
    python scripts/cleanup-all.py --mysql-only
    python scripts/cleanup-all.py --es-only
    python scripts/cleanup-all.py --minio-only
"""

import argparse
import json
import subprocess
import sys

import requests


def run(cmd, desc=""):
    """执行命令，打印结果"""
    label = f"  [{desc}]" if desc else ""
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if result.returncode != 0 and result.stderr:
        # docker exec cqlsh 会把正常输出也打到 stderr，"truncated" 不算错误
        stderr_lower = result.stderr.lower()
        if "truncated" not in stderr_lower and "warning" not in stderr_lower:
            print(f"  {label} FAIL: {result.stderr.strip()[:200]}")
            return False
    if result.stdout.strip():
        for line in result.stdout.strip().split("\n"):
            print(f"  {label} {line}")
    return True


def cleanup_cassandra():
    """清空 Cassandra 中 notes keyspace 的所有表"""
    print("\n[Cassandra] 清空 notes keyspace ...")
    tables = ["comment", "comment_like", "comment_like_count", "note"]
    for table in tables:
        cql = f"TRUNCATE notes.{table};"
        ok = run(
            f'docker exec cassandra cqlsh -e "{cql}"',
            desc=f"TRUNCATE {table}"
        )
        if not ok:
            print(f"  WARN: {table} 清空可能失败")
    print("[Cassandra] 完成")


def cleanup_mysql():
    """清空 MySQL 中 live_community 数据库的测试表"""
    print("\n[MySQL] 清空 live_community ...")
    tables = ["interaction_record", "note", "user_info", "user_wechat"]
    for table in tables:
        sql = f"DELETE FROM {table};"
        ok = run(
            f'docker exec mysql mysql -uroot -proot live_community -e "{sql}"',
            desc=f"DELETE {table}"
        )
        if not ok:
            print(f"  WARN: {table} 清空可能失败")
    # 重置 leaf_alloc 的 max_id（保留表结构，重置数据）
    run(
        'docker exec mysql mysql -uroot -proot live_community -e "UPDATE leaf_alloc SET max_id = 0 WHERE biz_tag IN (\'note\', \'comment\');"',
        desc="reset leaf_alloc"
    )
    print("[MySQL] 完成")


def cleanup_elasticsearch():
    """删除并重建 ES 索引"""
    print("\n[Elasticsearch] 重建索引 ...")
    es_url = "http://localhost:9200"

    # 删除旧索引
    for index in ["notes", "users"]:
        r = requests.delete(f"{es_url}/{index}")
        if r.ok:
            print(f"  [DELETE {index}] {r.json()}")

    # 重建 notes 索引
    notes_mapping = {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 1,
            "analysis": {
                "analyzer": {
                    "default": {"type": "standard"}
                }
            }
        },
        "mappings": {
            "dynamic": True,
            "properties": {
                "id": {"type": "long"},
                "user_id": {"type": "long"},
                "title": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {"keyword": {"type": "keyword"}}
                },
                "content": {"type": "text", "analyzer": "standard"},
                "summary": {"type": "text"},
                "tags": {"type": "text"},
                "category": {
                    "type": "text",
                    "fields": {"keyword": {"type": "keyword"}}
                },
                "status": {
                    "type": "text",
                    "fields": {"keyword": {"type": "keyword"}}
                },
                "cover_url": {"type": "keyword"},
                "view_count": {"type": "long"},
                "like_count": {"type": "long"},
                "favorite_count": {"type": "long"},
                "created_at": {"type": "date"}
            }
        }
    }
    r = requests.put(f"{es_url}/notes", json=notes_mapping)
    result = r.json()
    print(f"  [CREATE notes] acknowledged={result.get('acknowledged')}, "
          f"index={result.get('index')}")

    # 重建 users 索引
    users_mapping = {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 1,
            "analysis": {
                "analyzer": {
                    "default": {"type": "standard"}
                }
            }
        },
        "mappings": {
            "properties": {
                "id": {"type": "long"},
                "username": {
                    "type": "text",
                    "fields": {"keyword": {"type": "keyword"}}
                },
                "nickname": {
                    "type": "text",
                    "fields": {"suggest": {"type": "completion"}}
                },
                "avatar": {"type": "keyword"},
                "status": {"type": "keyword"}
            }
        }
    }
    r = requests.put(f"{es_url}/users", json=users_mapping)
    result = r.json()
    print(f"  [CREATE users] acknowledged={result.get('acknowledged')}, "
          f"index={result.get('index')}")

    print("[Elasticsearch] 完成")


def cleanup_minio():
    """清空 MinIO bucket"""
    print("\n[MinIO] 清空 buckets ...")
    run("docker exec minio mc alias set local http://localhost:9000 minioadmin minioadmin", desc="alias")
    run("docker exec minio mc rm --recursive --force local/notes/", desc="clear notes/")
    run("docker exec minio mc rm --recursive --force local/uploads/", desc="clear uploads/")
    print("[MinIO] 完成")


def main():
    parser = argparse.ArgumentParser(description="清空所有测试数据")
    parser.add_argument("--cassandra-only", action="store_true")
    parser.add_argument("--mysql-only", action="store_true")
    parser.add_argument("--es-only", action="store_true")
    parser.add_argument("--minio-only", action="store_true")
    args = parser.parse_args()

    only_flags = [args.cassandra_only, args.mysql_only, args.es_only, args.minio_only]
    run_all = not any(only_flags)

    print("=" * 50)
    print("  清空测试数据")
    print("=" * 50)

    if run_all or args.cassandra_only:
        cleanup_cassandra()
    if run_all or args.mysql_only:
        cleanup_mysql()
    if run_all or args.es_only:
        cleanup_elasticsearch()
    if run_all or args.minio_only:
        cleanup_minio()

    print()
    print("=" * 50)
    print("  全部完成！")
    print("=" * 50)


if __name__ == "__main__":
    main()
