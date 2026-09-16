#!/bin/sh

set -eu

alias_name=blindtest
bucket_path="$alias_name/$S3_BUCKET"

mc alias set "$alias_name" "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "$bucket_path"
mc anonymous set none "$bucket_path"
mc stat "$bucket_path"
