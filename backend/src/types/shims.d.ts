declare module 'cors' {
  const cors: any;
  export default cors;
}

declare module 'express' {
  export type Request = any;
  export type Response = any;
  export type NextFunction = any;
  export function Router(): any;
  const exp: any;
  export default exp;
}

declare module 'helmet' {
  const helmet: any;
  export default helmet;
}

declare module 'dotenv' {
  const dotenv: { config: () => void };
  export default dotenv;
}

declare module 'pg' {
  export class Pool {
    constructor(config?: any);
    query<T = any>(text: string, values?: unknown[]): Promise<{ rows: T[] }>;
  }
}

declare module 'axios' {
  const axios: any;
  export default axios;
}

declare module 'zod' {
  export const z: any;
}

declare module 'openai' {
  export default class OpenAI {
    constructor(config: any);
    chat: any;
  }
}

declare const process: {
  env: Record<string, string | undefined>;
};
