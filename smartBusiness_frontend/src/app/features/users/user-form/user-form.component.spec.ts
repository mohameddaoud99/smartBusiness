import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';

import { UserFormComponent } from './user-form.component';
import { UserResponse } from '../user.model';
import { RoleResponse } from '../../roles/role.model';
import { BranchResponse } from '../../branches/branch.model';

describe('UserFormComponent', () => {

  let fixture: ComponentFixture<UserFormComponent>;
  let component: UserFormComponent;
  let httpMock: HttpTestingController;

  const salesManager: RoleResponse = {
    id: 2,
    name: 'SALES_MANAGER',
    label: 'Sales Manager',
    system: true,
    permissions: ['SALE_VIEW', 'SALE_CREATE'],
    userCount: 1,
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00'
  };

  const tunis: BranchResponse = {
    id: 4,
    code: 'TUNIS',
    name: 'Tunis',
    status: 'ACTIVE',
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00'
  };

  const existingUser: UserResponse = {
    id: 7,
    firstName: 'Sonia',
    lastName: 'Trabelsi',
    fullName: 'Sonia Trabelsi',
    username: 's.trabelsi',
    email: 'sonia@example.com',
    phone: '+216 98 111 222',
    status: 'ACTIVE',
    branch: { id: 4, code: 'TUNIS', name: 'Tunis' },
    roles: [{ id: 2, name: 'SALES_MANAGER', label: 'Sales Manager', system: true }],
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UserFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    // The form loads the company's roles and branches as soon as it is created
    httpMock.expectOne('http://localhost:8080/api/roles').flush([salesManager]);
    httpMock.expectOne(req => req.url === 'http://localhost:8080/api/branches')
      .flush({ content: [tunis], totalElements: 1, totalPages: 1, size: 200, number: 0 });

    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('offers the roles returned by the API', () => {
    expect(component.roles().length).toBe(1);
    expect(component.roles()[0].label).toBe('Sales Manager');
  });

  it('starts invalid when empty', () => {
    component.open();
    expect(component.form.valid).toBeFalse();
  });

  it('requires a password when creating', () => {
    component.open();
    expect(component.form.controls['password'].hasError('required')).toBeTrue();
  });

  it('does not require a password when editing', () => {
    component.open(existingUser);
    expect(component.form.controls['password'].hasError('required')).toBeFalse();
    expect(component.editingId()).toBe(7);
  });

  it('prefills the form when editing, roles and branch included', () => {
    component.open(existingUser);
    expect(component.form.value.username).toBe('s.trabelsi');
    expect(component.form.value.roleIds).toEqual([2]);
    expect(component.form.value.branchId).toBe(4);
  });

  it('offers the branches returned by the API', () => {
    expect(component.branches().length).toBe(1);
    expect(component.branches()[0].name).toBe('Tunis');
  });

  it('a user with no branch prefills branchId as null', () => {
    component.open({ ...existingUser, branch: undefined });
    expect(component.form.value.branchId).toBeNull();
  });

  it('accepts a user with no role — that is the secure default', () => {
    component.open();
    component.form.patchValue({
      firstName: 'Sonia', lastName: 'Trabelsi', username: 's.trabelsi',
      email: 'sonia@example.com', password: 'Password123'
    });
    expect(component.form.valid).toBeTrue();
  });

  it('rejects an invalid email', () => {
    component.open();
    component.form.controls['email'].setValue('not-an-email');
    expect(component.form.controls['email'].hasError('email')).toBeTrue();
  });

  it('rejects a username containing spaces', () => {
    component.open();
    component.form.controls['username'].setValue('has spaces');
    expect(component.form.controls['username'].hasError('pattern')).toBeTrue();
  });

  it('rejects a password shorter than 8 characters', () => {
    component.open();
    component.form.controls['password'].setValue('short');
    expect(component.form.controls['password'].hasError('minlength')).toBeTrue();
  });

  it('does not call the API while the form is invalid', () => {
    component.open();
    component.submit();

    httpMock.expectNone('http://localhost:8080/api/users');
    expect(component.saving()).toBeFalse();
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs the selected role ids and branch when creating', () => {
    component.open();
    component.form.setValue({
      firstName: 'Sonia', lastName: 'Trabelsi', username: 's.trabelsi',
      email: 'sonia@example.com', phone: '', branchId: 4, roleIds: [2],
      status: 'ACTIVE', password: 'Password123'
    });

    component.submit();

    const req = httpMock.expectOne('http://localhost:8080/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.roleIds).toEqual([2]);
    expect(req.request.body.branchId).toBe(4);
    req.flush({ ...existingUser });
  });

  it('PUTs when editing an existing user', () => {
    component.open(existingUser);
    component.form.controls['firstName'].setValue('Sonya');

    component.submit();

    const req = httpMock.expectOne('http://localhost:8080/api/users/7');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...existingUser });
  });

  it('releases the saving state when the API returns an error', () => {
    component.open();
    component.form.setValue({
      firstName: 'Sonia', lastName: 'Trabelsi', username: 's.trabelsi',
      email: 'sonia@example.com', phone: '', branchId: null, roleIds: [],
      status: 'ACTIVE', password: 'Password123'
    });

    component.submit();
    expect(component.saving()).toBeTrue();

    httpMock.expectOne('http://localhost:8080/api/users')
      .flush({ message: 'This username is already taken' },
             { status: 409, statusText: 'Conflict' });

    expect(component.saving()).toBeFalse();
  });

  it('reports a readable message for each validation error', () => {
    component.open();
    component.form.controls['email'].setValue('nope');
    expect(component.errorFor('email')).toBe('Enter a valid email address.');

    component.form.controls['firstName'].setValue('');
    expect(component.errorFor('firstName')).toBe('This field is required.');
  });
});
