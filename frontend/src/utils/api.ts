/**
 * Utility for making authenticated API calls
 */

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

/**
 * Make an authenticated API request
 */
export const apiRequest = async (
  endpoint: string, 
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
  data?: any
) => {
  const token = localStorage.getItem('auth_token');
  
  const headers: HeadersInit = {
    'Content-Type': 'application/json'
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const config: RequestInit = {
    method,
    headers,
    credentials: 'include',
  };

  if (data) {
    config.body = JSON.stringify(data);
  }

  const response = await fetch(`${API_URL}${endpoint}`, config);
  
  if (!response.ok) {
    // Handle 401 Unauthorized - redirect to login
    if (response.status === 401) {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('user');
      window.location.href = '/login';
      throw new Error('Session expirée. Veuillez vous reconnecter.');
    }
    
    // Handle other errors
    const errorText = await response.text();
    throw new Error(errorText || 'Une erreur est survenue');
  }
  
  // Check if response is empty
  const text = await response.text();
  return text ? JSON.parse(text) : {};
}
