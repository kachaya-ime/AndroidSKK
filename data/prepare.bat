
curl -O https://raw.githubusercontent.com/skk-dev/dict/refs/heads/master/SKK-JISYO.L
curl -O https://raw.githubusercontent.com/skk-dev/dict/refs/heads/master/SKK-JISYO.jinmei
curl -O https://raw.githubusercontent.com/skk-dev/dict/refs/heads/master/SKK-JISYO.geo
curl -O https://raw.githubusercontent.com/skk-dev/dict/refs/heads/master/SKK-JISYO.station
curl -O https://raw.githubusercontent.com/skk-dev/dict/refs/heads/master/SKK-JISYO.propernoun

@rem http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict-raw/
curl -O http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict-raw/20260723/small_lex.zip
curl -O http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict-raw/20260723/core_lex.zip
curl -O http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict-raw/20260723/notcore_lex.zip

tar xf small_lex.zip
tar xf core_lex.zip
tar xf notcore_lex.zip
