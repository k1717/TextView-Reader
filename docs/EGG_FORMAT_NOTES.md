# EGG Format Notes (for EggArchiveReader)

Notes for the first-party `EggArchiveReader` implementation. These notes summarize public EGG container concepts and observed interoperability behavior so the parser can be maintained without repeatedly re-deriving the layout. No ESTsoft source files, unEGG source files, UnEGG binary module, or other third-party EGG decoder code is bundled in this repository.

## Magic numbers (little-endian uint32)
- EGG header:      0x41474745  ("EGGA")
- FILE chunk:      0x0a8590e3
- BLOCK chunk:     0x02b50c13
- ENCRYPT field:   0x08d1470f
- WINDOWS_FILEINFO 0x2c86950b
- POSIX_FILEINFO   0x1ee922e5
- FILENAME field:  0x0a8591ac
- COMMENT field:   0x04c63672
- SPLIT field:     0x24f5a262
- SOLID field:     0x24e5a060
- DUMMY:           0x07463307
- END:             0x08e28222

## Global EGG header (14 bytes)
- magic   uint32 = 0x41474745
- version uint16
- id      uint32
- reserved uint32

## FILE chunk (16 bytes, then extra fields)
- magic  uint32 = FILE
- id     uint32
- length uint64   (uncompressed file size)
- followed by a sequence of extra fields, terminated by END magic

## ExtraField common header
- signature uint32 (END terminates the field list)
- gpb       uint8  (general purpose bit flag)
- size:     if (gpb & 1) uint32 else uint16
- payload[size]

### FILENAME field payload
- bit flags via gpb (encrypt/relative/locale bits)
- locale uint16 (when present)
- name bytes (UTF-8 unless locale-encoded)

## BLOCK chunk (18 bytes header, then compressed data)
- magic      uint32 = BLOCK
- compMethod uint8   (0 Store, 1 Deflate, 2 bzip, 3 azo, 4 lzma)
- methodHint uint8
- uncompSize uint32
- compSize   uint32
- crc        uint32
- data[compSize]

## CompressionMethod enum
Store=0, Deflate=1, bzip=2, azo=3, lzma=4

## Support policy in this reader
- Store    -> copy
- Deflate  -> raw inflate (java.util.zip.Inflater nowrap)
- bzip     -> commons-compress BZip2CompressorInputStream
- lzma     -> org.tukaani:xz LZMAInputStream (dependency already present)
- azo      -> UNSUPPORTED (ESTsoft proprietary; no open decoder)
- Encrypted / split / solid -> handled as unsupported/password as appropriate
- CRC of each block is verified; mismatch deletes output and fails loudly.
