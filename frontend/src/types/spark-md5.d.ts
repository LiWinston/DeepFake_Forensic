declare module 'spark-md5' {
  export default class SparkMD5 {
    constructor();
    static ArrayBuffer: {
      new(): {
        append(arr: ArrayBuffer): void;
        end(raw?: boolean): string;
      };
      hash(arr: ArrayBuffer | string, raw?: boolean): string;
    };
    static hash(str: string, raw?: boolean): string;
  }
}