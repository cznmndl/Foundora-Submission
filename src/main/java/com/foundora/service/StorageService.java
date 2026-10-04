package com.foundora.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {

    /**
     * Returns the public URL of the uploaded image, or null if there is none.
     * TODO: upload to Supabase Storage. Images are currently discarded.
     */
    public String uploadImage(MultipartFile file) {
        return null;
    }
}
