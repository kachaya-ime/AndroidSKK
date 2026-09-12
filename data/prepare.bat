@rem http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict/
curl -L -O http://sudachi.s3-website-ap-northeast-1.amazonaws.com/sudachidict/sudachi-dictionary-20260723-full.zip
tar xf sudachi-dictionary-20260723-full.zip --strip-components 1

@rem sudachi辞書の品詞IDはunidic-mecab-2.1.2のもの(5918種類)を使用している
curl -O  https://clrd.ninjal.ac.jp/unidic_archive/cwj/2.1.2/unidic-mecab-2.1.2_src.zip
tar xf unidic-mecab-2.1.2_src.zip --strip-components 1 unidic-mecab-2.1.2_src/left-id.def
