// Miroir du ArticleResponseDto Java
export interface Article {
  id: number;
  title: string;
  content: string;
  author: string;
  createdAt: string;
  updatedAt: string;
}

// Miroir du ArticleRequestDto Java
export interface ArticleRequest {
  title: string;
  content: string;
  author: string;
}
