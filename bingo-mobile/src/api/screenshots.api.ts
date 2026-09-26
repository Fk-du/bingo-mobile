import apiClient from './client';
import { ApiResponse } from '@/types';

const API_PREFIX = '/api/v1';

/** A local file to upload (React Native asset/uri or web File). */
export type UploadFile = { uri: string; name?: string; type?: string };

export const screenshotsApi = {
  upload: async (file: UploadFile | { file: unknown }) => {
    const formData = new FormData();
    if ('file' in file) {
      // Web-style File
      formData.append('file', file.file as never);
    } else {
      // RN asset descriptor
      formData.append('file', {
        uri: file.uri,
        name: file.name ?? 'screenshot.jpg',
        type: file.type ?? 'image/jpeg',
      } as never);
    }
    const res = await apiClient.post<ApiResponse<string>>('/screenshots/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },
  fetchBlobUrl: async (url: string) => {
    if (!url.startsWith(API_PREFIX)) {
      return url;
    }
    const path = url.slice(API_PREFIX.length);
    const res = await apiClient.get<Blob>(path, { responseType: 'blob' });
    return URL.createObjectURL(res.data);
  },
};