DROP TABLE IF EXISTS schema_version;  -- 旧 Java 迁移的版本表，Flyway 接管后废弃

-- 7.4 纯 SQL 实现的 CJK 二元分词函数 (支持中文自然语言无依赖分词)
CREATE OR REPLACE FUNCTION cjk_bigram_tokenize(str TEXT) RETURNS TEXT AS $$
DECLARE
    len INT;
    i INT;
    c1 TEXT;
    c2 TEXT;
    result TEXT := '';
BEGIN
    IF str IS NULL OR str = '' THEN
        RETURN '';
    END IF;
    len := length(str);
    FOR i IN 1..len LOOP
        c1 := substr(str, i, 1);
        IF c1 ~ '[\u4e00-\u9fa5]' THEN
            result := result || ' ' || c1;
            IF i < len THEN
                c2 := substr(str, i + 1, 1);
                IF c2 ~ '[\u4e00-\u9fa5]' THEN
                    result := result || ' ' || c1 || c2;
                END IF;
            END IF;
        ELSEIF c1 ~ '[a-zA-Z0-9_]' THEN
            result := result || c1;
        ELSE
            result := result || ' ';
        END IF;
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 7.5 自动生成/同步 tsvector 的触发器函数 (CJK Bigram 倒排索引驱动)
CREATE OR REPLACE FUNCTION update_chunk_tsv() RETURNS trigger AS $$
BEGIN
    NEW.tsv_content := to_tsvector('simple', cjk_bigram_tokenize(COALESCE(NEW.content, '')));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chunk_tsv_update ON document_chunk;
CREATE TRIGGER trg_chunk_tsv_update
BEFORE INSERT OR UPDATE ON document_chunk
FOR EACH ROW EXECUTE FUNCTION update_chunk_tsv();
