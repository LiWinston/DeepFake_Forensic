import { describe, it, expect, vi, beforeEach } from 'vitest'
import { 
  formatFileSize, 
  formatDateTime, 
  getFileExtension, 
  isSupportedFileType, 
  getFileCategory, 
  generateId, 
  debounce, 
  throttle, 
  deepClone, 
  isEmpty, 
  capitalize, 
  toCamelCase, 
  toSnakeCase, 
  isValidEmail, 
  getRandomColor, 
  copyToClipboard, 
  downloadFile 
} from '../src/utils/index'

describe('Utility Functions', () => {
  describe('formatFileSize', () => {
    it('should format bytes correctly', () => {
      expect(formatFileSize(0)).toBe('0 Bytes')
      expect(formatFileSize(1024)).toBe('1 KB')
      expect(formatFileSize(1048576)).toBe('1 MB')
      expect(formatFileSize(1073741824)).toBe('1 GB')
    })

    it('should handle decimal values', () => {
      expect(formatFileSize(1536)).toBe('1.5 KB')
      expect(formatFileSize(2048)).toBe('2 KB')
    })
  })

  describe('formatDateTime', () => {
    it('should format date strings correctly', () => {
      const dateString = '2024-01-01T12:00:00Z'
      const result = formatDateTime(dateString)
      expect(result).toMatch(/\d{2}\/\d{2}\/\d{4}, \d{2}:\d{2}:\d{2}/)
    })

    it('should format Date objects correctly', () => {
      const date = new Date('2024-01-01T12:00:00Z')
      const result = formatDateTime(date)
      expect(result).toMatch(/\d{2}\/\d{2}\/\d{4}, \d{2}:\d{2}:\d{2}/)
    })
  })

  describe('getFileExtension', () => {
    it('should extract file extensions', () => {
      expect(getFileExtension('test.jpg')).toBe('jpg')
      expect(getFileExtension('document.pdf')).toBe('pdf')
      expect(getFileExtension('video.mp4')).toBe('mp4')
    })

    it('should handle files without extensions', () => {
      expect(getFileExtension('README')).toBe('readme') // The function converts to lowercase
      expect(getFileExtension('')).toBe('')
    })
  })

  describe('isValidEmail', () => {
    it('should validate correct email formats', () => {
      expect(isValidEmail('user@example.com')).toBe(true)
      expect(isValidEmail('test.email@domain.co.uk')).toBe(true)
      expect(isValidEmail('user+tag@example.org')).toBe(true)
    })

    it('should reject invalid email formats', () => {
      expect(isValidEmail('invalid-email')).toBe(false)
      expect(isValidEmail('user@')).toBe(false)
      expect(isValidEmail('@domain.com')).toBe(false)
      expect(isValidEmail('user@domain')).toBe(false)
      expect(isValidEmail('')).toBe(false)
    })
  })

  describe('generateId', () => {
    it('should generate unique IDs', () => {
      const id1 = generateId()
      const id2 = generateId()
      expect(id1).not.toBe(id2)
      expect(typeof id1).toBe('string')
      expect(id1.length).toBeGreaterThan(0)
    })
  })

  describe('debounce', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    it('should debounce function calls', () => {
      const mockFn = vi.fn()
      const debouncedFn = debounce(mockFn, 100)
      
      debouncedFn()
      debouncedFn()
      debouncedFn()
      
      expect(mockFn).not.toHaveBeenCalled()
      
      vi.advanceTimersByTime(100)
      expect(mockFn).toHaveBeenCalledTimes(1)
    })
  })

  describe('throttle', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    it('should throttle function calls', () => {
      const mockFn = vi.fn()
      const throttledFn = throttle(mockFn, 100)
      
      throttledFn()
      throttledFn()
      throttledFn()
      
      expect(mockFn).toHaveBeenCalledTimes(1)
      
      vi.advanceTimersByTime(100)
      throttledFn()
      expect(mockFn).toHaveBeenCalledTimes(2)
    })
  })

  describe('deepClone', () => {
    it('should deep clone objects', () => {
      const original = { a: 1, b: { c: 2 } }
      const cloned = deepClone(original)
      
      expect(cloned).toEqual(original)
      expect(cloned).not.toBe(original)
      expect(cloned.b).not.toBe(original.b)
    })

    it('should handle arrays', () => {
      const original = [1, { a: 2 }, [3, 4]]
      const cloned = deepClone(original)
      
      expect(cloned).toEqual(original)
      expect(cloned).not.toBe(original)
      expect(cloned[1]).not.toBe(original[1])
    })

    it('should handle primitives', () => {
      expect(deepClone(42)).toBe(42)
      expect(deepClone('test')).toBe('test')
      expect(deepClone(null)).toBe(null)
    })
  })

  describe('isEmpty', () => {
    it('should detect empty values', () => {
      expect(isEmpty(null)).toBe(true)
      expect(isEmpty(undefined)).toBe(true)
      expect(isEmpty('')).toBe(true)
      expect(isEmpty([])).toBe(true)
      expect(isEmpty({})).toBe(true)
    })

    it('should detect non-empty values', () => {
      expect(isEmpty('test')).toBe(false)
      expect(isEmpty([1, 2])).toBe(false)
      expect(isEmpty({ a: 1 })).toBe(false)
      expect(isEmpty(0)).toBe(false)
      expect(isEmpty(false)).toBe(false)
    })
  })

  describe('capitalize', () => {
    it('should capitalize strings', () => {
      expect(capitalize('hello')).toBe('Hello')
      expect(capitalize('WORLD')).toBe('World')
      expect(capitalize('test')).toBe('Test')
    })
  })

  describe('toCamelCase', () => {
    it('should convert snake_case to camelCase', () => {
      expect(toCamelCase('hello_world')).toBe('helloWorld')
      expect(toCamelCase('test_case_example')).toBe('testCaseExample')
    })
  })

  describe('toSnakeCase', () => {
    it('should convert camelCase to snake_case', () => {
      expect(toSnakeCase('helloWorld')).toBe('hello_world')
      expect(toSnakeCase('testCaseExample')).toBe('test_case_example')
    })
  })

  describe('getRandomColor', () => {
    it('should return a valid color', () => {
      const color = getRandomColor()
      expect(color).toMatch(/^#[0-9a-f]{6}$/i)
    })
  })

  describe('copyToClipboard', () => {
    it('should handle clipboard API', async () => {
      // Mock navigator.clipboard
      Object.assign(navigator, {
        clipboard: {
          writeText: vi.fn().mockResolvedValue(undefined)
        }
      })

      const result = await copyToClipboard('test text')
      expect(result).toBe(true)
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('test text')
    })

    it('should handle clipboard errors', async () => {
      // Mock navigator.clipboard with error
      Object.assign(navigator, {
        clipboard: {
          writeText: vi.fn().mockRejectedValue(new Error('Clipboard error'))
        }
      })

      const result = await copyToClipboard('test text')
      expect(result).toBe(false)
    })
  })

  describe('downloadFile', () => {
    it('should create download link', () => {
      // Mock DOM methods
      const mockClick = vi.fn()
      const mockAppendChild = vi.fn()
      const mockRemoveChild = vi.fn()
      
      const mockLink = {
        href: '',
        download: '',
        click: mockClick
      }
      
      vi.spyOn(document, 'createElement').mockReturnValue(mockLink as any)
      vi.spyOn(document.body, 'appendChild').mockImplementation(mockAppendChild)
      vi.spyOn(document.body, 'removeChild').mockImplementation(mockRemoveChild)

      downloadFile('http://example.com/file.pdf', 'test.pdf')
      
      expect(mockLink.href).toBe('http://example.com/file.pdf')
      expect(mockLink.download).toBe('test.pdf')
      expect(mockClick).toHaveBeenCalled()
      expect(mockAppendChild).toHaveBeenCalledWith(mockLink)
      expect(mockRemoveChild).toHaveBeenCalledWith(mockLink)
    })
  })
})
