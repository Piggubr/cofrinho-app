package com.piggu.common.web;

import com.piggu.common.error.BusinessException;

/**
 * Tipo da imagem pelos primeiros bytes, nunca pelo que o cliente declara. So JPEG, PNG
 * e WebP passam: os leitores de imagem (e o Leptonica do OCR) abrem muitos outros
 * formatos, cada um com seu historico de falhas de seguranca.
 */
public final class Imagens {

    private Imagens() {
    }

    /** Assinatura dos primeiros bytes: JPEG FF D8 FF, PNG 89 50 4E 47, WebP RIFF....WEBP. */
    public static String tipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException("A foto precisa ser JPEG, PNG ou WebP.");
    }
}
